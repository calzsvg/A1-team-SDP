package screen;

import java.awt.Color;
import java.awt.event.KeyEvent;
import engine.DrawManager.SpriteType;
import pvp.PvpClient;
import pvp.PvpMatch;
import pvp.PvpProtocol;
import pvp.PvpServerAddress;

/** Each client controls one ship and always sees it at the bottom. */
public final class OnlinePvpScreen extends Screen {
    private PvpClient client;
    private final Starfield stars;
    private boolean ready, readyWasDown, retryWasDown;
    private String address;
    private int port;
    private String configError;

    public OnlinePvpScreen(int width, int height, int fps) {
        super(width, height, fps);
        returnCode = 1;
        stars = new Starfield(width, height);
        address = PvpServerAddress.host();
        try {
            port = Integer.parseInt(System.getProperty("pvp.port", "7777"));
            if (port < 1 || port > 65535 || address.length() == 0)
                throw new NumberFormatException();
        } catch (NumberFormatException e) { configError = "Invalid pvp.host / pvp.port setting"; }
        readyWasDown = inputManager.isKeyDown(KeyEvent.VK_SPACE);
    }
    public void initialize() { connect(); }
    private void connect() {
        if (configError != null) return;
        if (client != null) client.close();
        ready = false;
        client = new PvpClient(PvpServerAddress.candidates(), port);
    }
    public int run() {
        try { super.run(); return returnCode; }
        finally { if (client != null) client.close(); }
    }
    protected void update() {
        boolean space = inputManager.isKeyDown(KeyEvent.VK_SPACE);
        boolean retry = inputManager.isKeyDown(KeyEvent.VK_R);
        if (inputDelay.checkFinished()) {
            if (inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) { isRunning = false; return; }
            if (retry && !retryWasDown && client != null
                    && (client.isClosed() || (client.getState() != null
                    && client.getState().phase == PvpMatch.FINISHED))) connect();
            if (space && !readyWasDown) ready = true;
        }
        readyWasDown = space; retryWasDown = retry;
        if (client != null) {
            int input = ready ? PvpMatch.READY : 0;
            if (inputManager.isKeyDown(KeyEvent.VK_A) || inputManager.isKeyDown(KeyEvent.VK_LEFT))
                input |= PvpMatch.LEFT;
            if (inputManager.isKeyDown(KeyEvent.VK_D) || inputManager.isKeyDown(KeyEvent.VK_RIGHT))
                input |= PvpMatch.RIGHT;
            if (space) input |= PvpMatch.FIRE;
            client.setInput(input);
        }
        stars.update(); draw();
    }
    private void draw() {
        drawManager.initDrawing(this);
        drawManager.drawStarfield(this, stars);
        drawManager.drawSmallString("ONLINE PVP", 12, 18, Color.GREEN);
        drawManager.drawSmallString(address + ":" + port, 12, 34, Color.GRAY);
        PvpProtocol.State s = client == null ? null : client.getState();
        if (s == null) {
            drawManager.drawMenuRow(this, configError != null ? configError : client.getStatus(),
                    height / 2, false, false);
        } else {
            int local = client.getPlayer();
            int ox = (width - PvpMatch.WIDTH) / 2;
            int oy = 52;
            drawManager.drawBox(ox, oy, PvpMatch.WIDTH, PvpMatch.HEIGHT, Color.DARK_GRAY);
            for (int p = 0; p < 2; p++) {
                int x = ox + PvpMatch.viewX(s.x[p], PvpMatch.SHIP_W, local);
                int y = oy + PvpMatch.viewY(s.y[p], PvpMatch.SHIP_H, local);
                drawManager.drawPvpShip(x, y, p != local, p == local ? Color.GREEN : Color.RED);
            }
            for (int[] shot : s.shots) {
                drawManager.fillBox(ox + PvpMatch.viewX(shot[1], 4, local),
                        oy + PvpMatch.viewY(shot[2], 8, local), 4, 8,
                        shot[0] == local ? Color.GREEN : Color.RED);
            }
            drawManager.drawSmallString("YOU " + s.hp[local] + "    OPPONENT " + s.hp[1 - local],
                    12, 48, Color.WHITE);
            String message = null;
            if (s.phase == PvpMatch.FINISHED) message =
                    s.winner < 0 ? "DRAW" : s.winner == local ? "YOU WIN" : "YOU LOSE";
            else if (client.isClosed()) message = client.getStatus();
            else if (s.players < 2) message = "Waiting for opponent...";
            else if (s.phase == PvpMatch.WAITING) message =
                    "YOU " + (ready ? "READY" : "SPACE to ready")
                    + " / OPP " + (s.ready[1 - local] ? "READY" : "WAITING");
            else if (s.phase == PvpMatch.COUNTDOWN) message = "Starting in " + (s.ticksLeft + 59) / 60;
            if (message != null) drawManager.drawMenuRow(this, message, height / 2, false, false);
        }
        drawManager.drawSmallString("A/D move  SPACE fire/ready  R rejoin  ESC back",
                12, height - 32, Color.GRAY);
        drawManager.completeDrawing(this);
    }
}
