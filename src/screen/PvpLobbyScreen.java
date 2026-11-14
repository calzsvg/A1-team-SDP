package screen;

import java.awt.Color;
import java.awt.event.KeyEvent;
import engine.soundEngine.MenuSounds;
import pvp.PvpClient;
import pvp.PvpMatch;
import pvp.PvpProtocol;

/**
 * Online lobby preserving the original two-player panel layout.
 * The local player is below the opponent. Each client controls its own readiness;
 * the server owns the countdown. The connection stays open for the match.
 */
public final class PvpLobbyScreen extends Screen {
    public static final int PVP_GAME_CODE = 9;
    public static final int SETUP_CODE = 8;
    private static final int MENU_CODE = 1;
    private final PvpClient client;
    private final String roomCode;
    private final Starfield starfield;
    private boolean localReady, readyWasDown, escapeWasDown, retryWasDown;

    public PvpLobbyScreen(int width, int height, int fps, PvpClient client, String roomCode) {
        super(width, height, fps);
        this.client = client;
        this.roomCode = roomCode;
        returnCode = MENU_CODE;
        starfield = new Starfield(width, height);
        readyWasDown = inputManager.isKeyDown(KeyEvent.VK_SPACE);
        escapeWasDown = inputManager.isKeyDown(KeyEvent.VK_ESCAPE);
        retryWasDown = inputManager.isKeyDown(KeyEvent.VK_R);
        client.setInput(0);
    }

    public int run() { super.run(); return returnCode; }

    protected void update() {
        boolean space = inputManager.isKeyDown(KeyEvent.VK_SPACE);
        boolean escape = inputManager.isKeyDown(KeyEvent.VK_ESCAPE);
        boolean retry = inputManager.isKeyDown(KeyEvent.VK_R);
        boolean readyPressed = space && !readyWasDown;
        boolean escapePressed = escape && !escapeWasDown;
        boolean retryPressed = retry && !retryWasDown;
        readyWasDown = space; escapeWasDown = escape; retryWasDown = retry;
        PvpProtocol.State state = client.getState();
        if (inputDelay.checkFinished() && escapePressed) {
            client.setInput(0);
            returnCode = MENU_CODE; isRunning = false; return;
        }
        if (client.isClosed()) {
            if (inputDelay.checkFinished() && retryPressed) {
                returnCode = SETUP_CODE; isRunning = false; return;
            }
        } else {
            if (state != null && (state.phase == PvpMatch.PLAYING || state.phase == PvpMatch.FINISHED)) {
                client.setInput(0);
                returnCode = PVP_GAME_CODE; isRunning = false; return;
            }
            if (state != null && state.phase == PvpMatch.WAITING
                    && inputDelay.checkFinished() && readyPressed) {
                localReady = !localReady;
                MenuSounds.playMove();
            }
            // Never send movement or fire while the lobby owns the client.
            client.setInput(localReady ? PvpMatch.READY : 0);
        }
        starfield.update(); draw();
    }

    public boolean bothReady() {
        PvpProtocol.State state = client.getState();
        return state != null && state.players == 2 && state.ready[0] && state.ready[1];
    }

    private void draw() {
        drawManager.initDrawing(this);
        drawManager.drawStarfield(this, starfield);
        drawManager.drawScreenTitle(this, "PvP");
        drawManager.drawSmallString("ROOM " + roomCode, 12, 22, Color.GRAY);
        PvpProtocol.State state = client.getState();
        int local = client.getPlayer();
        boolean connected = state != null && !client.isClosed();
        boolean opponentPresent = connected && state.players == 2;
        drawPlayerPanel("OPPONENT", "REMOTE PLAYER", opponentPresent,
                opponentPresent && state.ready[1 - local], false, height * 2 / 9);
        drawManager.drawMenuRow(this, "VS", height / 2, false);
        drawPlayerPanel("YOU", "A/D or arrows move - SPACE fire", connected,
                connected && (state.players < 2 ? localReady : state.ready[local]), true, height * 3 / 5);
        String message;
        if (client.isClosed() || state == null) message = client.getStatus();
        else if (state.phase == PvpMatch.COUNTDOWN)
            message = "Starting in " + (state.ticksLeft + 59) / 60;
        else if (!opponentPresent) message = "Waiting for opponent...";
        else message = bothReady() ? "Both players ready" : "Waiting for both players";
        drawManager.drawMenuRow(this, message, height * 7 / 8,
                connected && state.phase == PvpMatch.COUNTDOWN);
        drawManager.drawSmallString(client.isClosed() ? "R setup  ESC back"
                : "SPACE toggle ready  ESC back", 12, height - 32, Color.GRAY);
        drawManager.completeDrawing(this);
    }

    /** Retains the original name / ship / controls / readiness panel structure. */
    private void drawPlayerPanel(String name, String keys, boolean present,
            boolean ready, boolean local, int top) {
        final int row = 24;
        drawManager.drawMenuRow(this, name, top, false);
        drawManager.drawPvpShip(width / 2 - 13, top + row / 2, !local,
                present ? (local ? Color.GREEN : Color.RED) : Color.DARK_GRAY);
        drawManager.drawMenuRow(this, keys, top + row * 2 + 4, false, false);
        String status = !present ? (local ? "Connecting..." : "Not connected")
                : ready ? "Ready" : local ? "Press SPACE to ready" : "Not ready";
        drawManager.drawMenuRow(this, status, top + row * 3 + 8, present && ready);
    }
}
