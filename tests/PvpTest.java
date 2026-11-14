import pvp.PvpMatch;
import pvp.PvpClient;
import pvp.PvpProtocol;
import pvp.PvpServer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Standalone deterministic simulation and real two-client TCP checks. */
public final class PvpTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void await(java.util.function.BooleanSupplier condition, String message)
            throws Exception {
        long until = System.nanoTime() + 8000000000L;
        while (!condition.getAsBoolean() && System.nanoTime() < until) Thread.sleep(10);
        check(condition.getAsBoolean(), message);
    }
    public static void main(String[] args) throws Exception {
        PvpMatch match = new PvpMatch();
        match.tick(PvpMatch.READY, 0);
        check(match.phase == PvpMatch.WAITING, "Cannot start with only one ready");
        match.tick(PvpMatch.READY, PvpMatch.READY);
        check(match.phase == PvpMatch.COUNTDOWN, "Both ready start countdown");
        for (int i = 0; i < 180; i++) match.tick(0, 0);
        check(match.phase == PvpMatch.PLAYING, "Countdown completes");
        int a = match.x[0], b = match.x[1];
        match.tick(PvpMatch.RIGHT, PvpMatch.RIGHT);
        check(match.x[0] == a + 4 && match.x[1] == b - 4, "View-relative right input");
        check(PvpMatch.viewX(match.x[1], 26, 1) == PvpMatch.WIDTH - b - 26 + 4,
                "Player two sees rightward movement");
        for (int p = 0; p < 2; p++) {
            check(PvpMatch.viewY(match.y[p], 16, p) == 360, "Each player at bottom");
            check(PvpMatch.viewY(match.y[1 - p], 16, p) == 24, "Opponent at top");
        }
        check(PvpMatch.viewX(PvpMatch.viewX(70, 4, 1), 4, 1) == 70,
                "Rotation is reversible for bullets");
        for (int i = 0; i < 200; i++) match.tick(PvpMatch.RIGHT, PvpMatch.RIGHT);
        check(match.x[0] == 374 && match.x[1] == 0, "Arena boundaries");
        match.x[0] = match.x[1] = 187;
        for (int i = 0; i < 600 && match.phase != PvpMatch.FINISHED; i++)
            match.tick(PvpMatch.FIRE, 0);
        check(match.phase == PvpMatch.FINISHED && match.winner == 0 && match.hp[1] == 0,
                "Bullets, damage and authoritative win");
        int hp = match.hp[0]; match.tick(0, PvpMatch.FIRE);
        check(match.hp[0] == hp, "Finished arena does not advance");
        PvpMatch draw = new PvpMatch(); draw.phase = PvpMatch.PLAYING;
        draw.hp[0] = draw.hp[1] = 1;
        draw.shots.add(new PvpMatch.Shot(0, 190, draw.y[1] + 6));
        draw.shots.add(new PvpMatch.Shot(1, 190, draw.y[0] - 6));
        draw.tick(0, 0);
        check(draw.phase == PvpMatch.FINISHED && draw.winner == -1, "Simultaneous hit draws");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PvpProtocol.write(new DataOutputStream(bytes), PvpProtocol.capture(match, 2));
        PvpProtocol.State decoded = PvpProtocol.read(new DataInputStream(
                new ByteArrayInputStream(bytes.toByteArray())));
        check(decoded.winner == 0 && decoded.hp[1] == 0, "State protocol roundtrip");
        byte[] invalid = bytes.toByteArray(); invalid[3] = 99;
        boolean rejected = false;
        try { PvpProtocol.read(new DataInputStream(new ByteArrayInputStream(invalid))); }
        catch (IOException expected) { rejected = true; }
        check(rejected, "Invalid state is rejected");

        final PvpServer server = new PvpServer("127.0.0.1", 0);
        final Throwable[] failure = new Throwable[1];
        Thread thread = new Thread(() -> {
            try { server.serve(); } catch (Throwable e) { failure[0] = e; }
        });
        thread.start();
        PvpClient c0 = new PvpClient("127.0.0.1", server.getPort());
        PvpClient c1 = null;
        try {
            await(() -> c0.getState() != null, "First client receives waiting state");
            check(c0.getPlayer() == 0 && c0.getState().players == 1, "First slot waits");
            final PvpClient second = new PvpClient("127.0.0.1", server.getPort()); c1 = second;
            await(() -> second.getState() != null && c0.getState().players == 2, "Clients paired");
            check(second.getPlayer() == 1, "Distinct second slot");
            c0.setInput(PvpMatch.READY); second.setInput(PvpMatch.READY);
            await(() -> second.getState().phase == PvpMatch.PLAYING, "Network ready/countdown");
            c0.setInput(PvpMatch.READY | PvpMatch.RIGHT);
            second.setInput(PvpMatch.READY | PvpMatch.RIGHT);
            await(() -> c0.getState().x[0] == 374 && second.getState().x[1] == 0,
                    "Two-client view-relative movement reaches opposite world edges");
            c0.close();
            await(() -> second.isClosed(), "Disconnect tears down room and informs opponent");
        } finally {
            c0.close(); if (c1 != null) c1.close(); server.close(); thread.join(3000);
        }
        check(!thread.isAlive() && failure[0] == null, "Server shuts down cleanly");
        System.out.println("PvP checks passed: " + checks);
    }
}
