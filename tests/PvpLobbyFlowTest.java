import engine.Core;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import pvp.PvpClient;
import pvp.PvpMatch;
import pvp.PvpServer;
import screen.PvpLobbyScreen;
import screen.PvpMatchScreen;
import screen.Screen;

/** Real TCP regression for authoritative lobby readiness and connection handoff. */
public final class PvpLobbyFlowTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + 8000000000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        check(condition.getAsBoolean(), message);
    }
    public static void main(String[] args) throws Exception {
        PvpServer server = new PvpServer("127.0.0.1", 0);
        Thread serving = new Thread(() -> {
            try { server.serve(); } catch (Exception e) { throw new RuntimeException(e); }
        });
        serving.start();
        try (PvpClient first = new PvpClient("127.0.0.1", server.getPort(), "045");
             PvpClient second = new PvpClient("127.0.0.1", server.getPort(), "045")) {
            await(() -> first.getState() != null && second.getState() != null
                    && first.getState().players == 2, "Players connect to one room");
            PvpLobbyScreen lobby = new PvpLobbyScreen(432, 497, 60, first, "045");
            check(!lobby.bothReady(), "Lobby initially waits for readiness");
            first.setInput(PvpMatch.READY);
            await(() -> first.getState().ready[first.getPlayer()], "Server acknowledges local readiness");
            check(!lobby.bothReady(), "Local readiness cannot ready the opponent");
            second.setInput(PvpMatch.READY);
            await(() -> lobby.bothReady(), "Lobby reflects both server readiness states");
            await(() -> first.getState().phase == PvpMatch.PLAYING, "Server completes countdown");

            // No frame is needed: a playing snapshot must exit before drawing another lobby frame.
            Method update = PvpLobbyScreen.class.getDeclaredMethod("update");
            update.setAccessible(true); update.invoke(lobby);
            Field next = Screen.class.getDeclaredField("returnCode"); next.setAccessible(true);
            check(next.getInt(lobby) == PvpLobbyScreen.PVP_GAME_CODE, "Lobby requests the match screen");
            check(!first.isClosed() && !second.isClosed(), "Lobby handoff keeps both sockets open");
            PvpMatchScreen match = new PvpMatchScreen(432, 497, 60, first, "045");
            Field connection = PvpMatchScreen.class.getDeclaredField("client"); connection.setAccessible(true);
            check(connection.get(match) == first, "Match uses the original lobby client");
            check(first.getState().shots.length == 0, "Lobby readiness does not fire a shot");
            ((PvpClient) connection.get(match)).setInput(PvpMatch.FIRE);
            await(() -> first.getState().phase == PvpMatch.FINISHED
                    && second.getState().phase == PvpMatch.FINISHED, "Original room finishes after handoff");
            check(first.getState().winner == first.getPlayer(), "Shared match client can play to victory");
            first.close();
            await(() -> second.isClosed(), "Closing session disconnects opponent");
        } finally {
            Core.getInputManager().setTextInputEnabled(false);
            server.close(); serving.join(3000);
        }
        check(!serving.isAlive(), "Test server stops cleanly");
        System.out.println("PvP lobby flow checks passed: " + checks);
    }
}
