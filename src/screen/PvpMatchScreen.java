package screen;

import java.awt.Color;
import java.awt.event.KeyEvent;
import pvp.PvpClient;
import pvp.PvpMatch;
import pvp.PvpProtocol;

/** Match and results view using the connection handed over by PvpLobbyScreen. */
public final class PvpMatchScreen extends Screen {
    private final PvpClient client;
    private final String roomCode;
    private final Starfield stars;
    private boolean retryWasDown;

    public PvpMatchScreen(int width, int height, int fps, PvpClient client, String roomCode) {
        super(width, height, fps);
        this.client = client; this.roomCode = roomCode;
        stars = new Starfield(width, height);
        returnCode = 1;
        retryWasDown = inputManager.isKeyDown(KeyEvent.VK_R);
    }

    public int run() { super.run(); return returnCode; }

    protected void update() {
        PvpProtocol.State state = client.getState();
        boolean retry = inputManager.isKeyDown(KeyEvent.VK_R);
        boolean ended = client.isClosed() || state != null && state.phase == PvpMatch.FINISHED;
        if (inputDelay.checkFinished()) {
            if (inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) { isRunning = false; return; }
            if (ended && retry && !retryWasDown) {
                returnCode = PvpLobbyScreen.SETUP_CODE; isRunning = false; return;
            }
        }
        retryWasDown = retry;
        int input = 0;
        if (!ended) {
            if (inputManager.isKeyDown(KeyEvent.VK_A) || inputManager.isKeyDown(KeyEvent.VK_LEFT))
                input |= PvpMatch.LEFT;
            if (inputManager.isKeyDown(KeyEvent.VK_D) || inputManager.isKeyDown(KeyEvent.VK_RIGHT))
                input |= PvpMatch.RIGHT;
            if (inputManager.isKeyDown(KeyEvent.VK_SPACE)) input |= PvpMatch.FIRE;
        }
        client.setInput(input);
        stars.update(); draw(state);
    }

    private void draw(PvpProtocol.State state) {
        drawManager.initDrawing(this);
        drawManager.drawStarfield(this, stars);
        drawManager.drawSmallString("ONLINE PVP", 12, 18, Color.GREEN);
        drawManager.drawSmallString("ROOM " + roomCode, 12, 34, Color.GRAY);
        if (state != null) {
            int local = client.getPlayer();
            int ox = (width - PvpMatch.WIDTH) / 2, oy = 52;
            drawManager.drawBox(ox, oy, PvpMatch.WIDTH, PvpMatch.HEIGHT, Color.DARK_GRAY);
            for (int p = 0; p < 2; p++) {
                drawManager.drawPvpShip(ox + PvpMatch.viewX(state.x[p], PvpMatch.SHIP_W, local),
                        oy + PvpMatch.viewY(state.y[p], PvpMatch.SHIP_H, local), p != local,
                        p == local ? Color.GREEN : Color.RED);
            }
            for (int[] shot : state.shots) {
                drawManager.fillBox(ox + PvpMatch.viewX(shot[1], 4, local),
                        oy + PvpMatch.viewY(shot[2], 8, local), 4, 8,
                        shot[0] == local ? Color.GREEN : Color.RED);
            }
            drawManager.drawSmallString("YOU " + state.hp[local] + "    OPPONENT " + state.hp[1 - local],
                    12, 48, Color.WHITE);
            String message = null;
            if (state.phase == PvpMatch.FINISHED)
                message = state.winner < 0 ? "DRAW" : state.winner == local ? "YOU WIN" : "YOU LOSE";
            else if (client.isClosed()) message = client.getStatus();
            if (message != null) drawManager.drawMenuRow(this, message, height / 2, true);
        } else drawManager.drawMenuRow(this, client.getStatus(), height / 2, false);
        drawManager.drawSmallString("A/D move  SPACE fire  R setup  ESC back", 12, height - 32, Color.GRAY);
        drawManager.completeDrawing(this);
    }
}
