import pvp.PvpClient;
import pvp.PvpMatch;
import pvp.PvpProtocol;
import pvp.PvpServer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Random;

/** Standalone checks for Normal / Augment mode selection, augments and the best-of-five series. */
public final class PvpModeSeriesTest {
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
    /** Starts a match in the given mode and runs the countdown. */
    private static PvpMatch started(boolean augment) {
        PvpMatch match = new PvpMatch(new Random(17));
        int mode = augment ? PvpMatch.AUGMENT : 0;
        match.tick(mode, 0);
        match.tick(PvpMatch.READY | mode, PvpMatch.READY);
        for (int i = 0; i < PvpMatch.COUNTDOWN_TICKS; i++) match.tick(0, 0);
        return match;
    }
    /** Ends the current round in favour of the winner (-1 for a draw). */
    private static void endRound(PvpMatch match, int winner) {
        if (winner != 0) match.hp[0] = 0;
        if (winner != 1) match.hp[1] = 0;
        match.tick(0, 0);
    }
    private static void nextRound(PvpMatch match) {
        for (int i = 0; i < PvpMatch.ROUND_OVER_TICKS; i++) match.tick(0, 0);
    }

    public static void main(String[] args) throws Exception {
        // Mode selection: only the host chooses, and only before getting ready.
        PvpMatch lobby = new PvpMatch();
        check(lobby.mode == PvpMatch.NORMAL, "Rooms start in Normal mode");
        lobby.tick(0, PvpMatch.AUGMENT);
        check(lobby.mode == PvpMatch.NORMAL, "Guest cannot change the mode");
        lobby.tick(PvpMatch.AUGMENT, 0);
        check(lobby.mode == PvpMatch.AUGMENT_MODE, "Host selects Augment mode");
        lobby.tick(PvpMatch.READY, 0);
        check(lobby.mode == PvpMatch.AUGMENT_MODE, "Mode is locked once the host is ready");
        lobby.tick(PvpMatch.READY | PvpMatch.AUGMENT, PvpMatch.READY);
        check(lobby.phase == PvpMatch.COUNTDOWN && lobby.mode == PvpMatch.AUGMENT_MODE,
                "Chosen mode carries into the countdown");

        // Normal mode never spawns augments.
        PvpMatch normal = started(false);
        for (int i = 0; i < 1200; i++) normal.tick(0, 0);
        check(normal.augments.isEmpty() && normal.effects[0] == 0, "Normal mode has no augments");

        // Augment mode: the first augment appears in the centre and drifts into a lane.
        PvpMatch match = started(true);
        for (int i = 0; i < PvpMatch.FIRST_AUGMENT_TICKS - 1; i++) match.tick(0, 0);
        check(match.augments.isEmpty(), "No augment before the first spawn delay");
        match.tick(0, 0);
        check(match.augments.size() == 1, "First augment spawns after the delay");
        PvpMatch.Augment first = match.augments.get(0);
        int kind = first.kind;
        check(first.y > 150 && first.y < 250, "Augment spawns near the centre");
        match.x[0] = 0; match.x[1] = PvpMatch.WIDTH - PvpMatch.SHIP_W;
        for (int i = 0; i < 300 && !match.augments.isEmpty(); i++) {
            PvpMatch.Augment a = match.augments.get(0);
            if (a.y > 300) match.x[0] = Math.max(0, Math.min(PvpMatch.WIDTH - PvpMatch.SHIP_W, a.x - 7));
            match.tick(0, 0);
        }
        check(match.augments.isEmpty(), "Player collects the augment by moving into it");
        boolean granted = kind == PvpMatch.EXTRA_LIFE ? match.hp[0] == PvpMatch.START_HP + 1
                : match.effects[0] != 0;
        check(granted && match.effects[1] == 0, "Only the collector receives the augment");

        // Each augment's effect.
        PvpMatch effects = started(true);
        effects.x[1] = 0; // keep the target out of the line of fire
        effects.apply(0, PvpMatch.TRIPLE_SHOT);
        effects.tick(PvpMatch.FIRE, 0);
        check(effects.shots.size() == 3, "Triple Shot fires three bullets");
        effects.shots.clear();
        effects.apply(0, PvpMatch.RAPID_FIRE);
        effects.effects[0] &= ~PvpMatch.EFFECT_TRIPLE;
        for (int i = 0; i < 36; i++) effects.tick(PvpMatch.FIRE, 0);
        check(effects.shots.size() == 3, "Rapid Fire halves the cooldown (two shots without it)");
        effects.apply(1, PvpMatch.EXTRA_LIFE);
        check(effects.hp[1] == PvpMatch.START_HP + 1, "Extra Life adds a life");
        for (int i = 0; i < 10; i++) effects.apply(1, PvpMatch.EXTRA_LIFE);
        check(effects.hp[1] == PvpMatch.MAX_HP, "Lives are capped");
        effects.shots.clear();
        effects.x[0] = 10;
        effects.tick(PvpMatch.SKILL, 0);
        check(effects.x[0] == 10, "Teleport needs the augment");
        effects.apply(0, PvpMatch.TELEPORT);
        effects.tick(0, 0);
        effects.tick(PvpMatch.SKILL, 0);
        check(effects.x[0] == PvpMatch.WIDTH - PvpMatch.SHIP_W - 10, "Teleport mirrors the ship");
        effects.tick(PvpMatch.SKILL, 0);
        check(effects.x[0] == PvpMatch.WIDTH - PvpMatch.SHIP_W - 10, "Holding the key teleports once");
        effects.tick(0, 0); effects.tick(PvpMatch.SKILL, 0);
        check(effects.x[0] == PvpMatch.WIDTH - PvpMatch.SHIP_W - 10, "Teleport has a cooldown");

        // Augments only last for the current round.
        endRound(effects, 0);
        check(effects.phase == PvpMatch.ROUND_OVER && effects.wins[0] == 1, "Round win is counted");
        nextRound(effects);
        check(effects.phase == PvpMatch.PLAYING && effects.round == 2, "Next round starts");
        check(effects.effects[0] == 0 && effects.effects[1] == 0 && effects.augments.isEmpty()
                && effects.shots.isEmpty() && effects.hp[0] == PvpMatch.START_HP
                && effects.hp[1] == PvpMatch.START_HP, "Augments and lives reset between rounds");
        check(effects.mode == PvpMatch.AUGMENT_MODE, "Mode stays for the whole series");

        // Best of five: first to three round wins.
        PvpMatch series = started(false);
        endRound(series, 1); nextRound(series);
        endRound(series, 0); nextRound(series);
        endRound(series, 0); nextRound(series);
        check(series.phase == PvpMatch.PLAYING && series.round == 4, "Series continues at 2 : 1");
        endRound(series, 0);
        check(series.phase == PvpMatch.FINISHED && series.winner == 0
                && series.wins[0] == 3 && series.wins[1] == 1, "Three wins decide the series early");

        PvpMatch drawn = started(false);
        endRound(drawn, 0); nextRound(drawn);
        endRound(drawn, 1); nextRound(drawn);
        endRound(drawn, 0); nextRound(drawn);
        endRound(drawn, 1); nextRound(drawn);
        endRound(drawn, -1);
        check(drawn.phase == PvpMatch.FINISHED && drawn.winner == -1 && drawn.round == 5,
                "Tied series after five rounds is a draw");

        PvpMatch decided = started(false);
        endRound(decided, -1); nextRound(decided);
        endRound(decided, -1); nextRound(decided);
        endRound(decided, -1); nextRound(decided);
        endRound(decided, -1); nextRound(decided);
        endRound(decided, 1);
        check(decided.phase == PvpMatch.FINISHED && decided.winner == 1,
                "After five rounds the player with more wins takes the series");

        // Protocol carries the mode, series and augments.
        PvpMatch snapshot = started(true);
        snapshot.wins[1] = 2; snapshot.effects[0] = PvpMatch.EFFECT_TRIPLE | PvpMatch.EFFECT_TELEPORT;
        snapshot.augments.add(new PvpMatch.Augment(PvpMatch.TELEPORT, 50, 60, 2, 0));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PvpProtocol.write(new DataOutputStream(bytes), PvpProtocol.capture(snapshot, 2));
        PvpProtocol.State decoded = PvpProtocol.read(new DataInputStream(
                new ByteArrayInputStream(bytes.toByteArray())));
        check(decoded.mode == PvpMatch.AUGMENT_MODE && decoded.round == 1 && decoded.wins[1] == 2
                && decoded.effects[0] == 5 && decoded.augments.length == 1
                && decoded.augments[0][0] == PvpMatch.TELEPORT && decoded.augments[0][1] == 50,
                "State protocol roundtrip with mode, series and augments");
        byte[] invalid = bytes.toByteArray(); invalid[19] = 7;
        boolean rejected = false;
        try { PvpProtocol.read(new DataInputStream(new ByteArrayInputStream(invalid))); }
        catch (IOException expected) { rejected = true; }
        check(rejected, "Invalid mode is rejected");

        // Over the network the guest sees the host's choice before the match starts.
        final PvpServer server = new PvpServer("127.0.0.1", 0);
        Thread thread = new Thread(() -> {
            try { server.serve(); } catch (IOException ignored) { }
        });
        thread.start();
        try (PvpClient host = new PvpClient("127.0.0.1", server.getPort(), "017");
             PvpClient guest = new PvpClient("127.0.0.1", server.getPort(), "017")) {
            await(() -> host.getState() != null && guest.getState() != null
                    && host.getState().players == 2, "Host and guest share a room");
            guest.setInput(PvpMatch.AUGMENT);
            Thread.sleep(200);
            check(guest.getState().mode == PvpMatch.NORMAL, "Guest input does not change the mode");
            host.setInput(PvpMatch.AUGMENT);
            await(() -> guest.getState().mode == PvpMatch.AUGMENT_MODE, "Guest sees the host's mode");
            host.setInput(PvpMatch.READY | PvpMatch.AUGMENT); guest.setInput(PvpMatch.READY);
            await(() -> guest.getState().phase == PvpMatch.PLAYING
                    && guest.getState().mode == PvpMatch.AUGMENT_MODE, "Match starts in the chosen mode");
        } finally {
            server.close(); thread.join(3000);
        }
        System.out.println("PvP mode and series checks passed: " + checks);
    }
}
