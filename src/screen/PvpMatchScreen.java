package screen;

import java.awt.Color;
import java.awt.event.KeyEvent;
import pvp.PvpClient;
import pvp.PvpMatch;
import pvp.PvpProtocol;

/** Match and results view using the connection handed over by PvpLobbyScreen. */
public final class PvpMatchScreen extends Screen {
    /** Labels and colours indexed by augment kind. */
    private static final String[] AUGMENT_LETTERS = {"T", "R", "+", "P"};
    private static final Color[] AUGMENT_COLORS = {new Color(80, 220, 255),
            new Color(255, 220, 70), new Color(255, 110, 200), new Color(150, 130, 255)};
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
            if (inputManager.isKeyDown(KeyEvent.VK_S) || inputManager.isKeyDown(KeyEvent.VK_DOWN)
                    || inputManager.isKeyDown(KeyEvent.VK_SHIFT)) input |= PvpMatch.SKILL;
        }
        client.setInput(input);
        stars.update(); draw(state);
    }

    private void drawRight(String text, int y, Color color) {
        drawManager.drawSmallString(text, width - 12 - drawManager.getSmallStringWidth(text), y, color);
    }

    private static String effectNames(int effects) {
        StringBuilder names = new StringBuilder();
        if ((effects & PvpMatch.EFFECT_TRIPLE) != 0) names.append(" TRIPLE");
        if ((effects & PvpMatch.EFFECT_RAPID) != 0) names.append(" RAPID");
        if ((effects & PvpMatch.EFFECT_TELEPORT) != 0) names.append(" TELEPORT");
        return names.length() == 0 ? " -" : names.toString();
    }

    private void draw(PvpProtocol.State state) {
        drawManager.initDrawing(this);
        drawManager.drawStarfield(this, stars);
        boolean augment = state != null && state.mode == PvpMatch.AUGMENT_MODE;
        drawManager.drawSmallString(augment ? "ONLINE PVP - AUGMENT" : "ONLINE PVP - NORMAL", 12, 18, Color.GREEN);
        drawManager.drawSmallString("ROOM " + roomCode, 12, 34, Color.GRAY);
        if (state != null) {
            int local = client.getPlayer();
            int ox = (width - PvpMatch.WIDTH) / 2, oy = 52;
            drawRight("ROUND " + state.round + "/" + PvpMatch.ROUNDS, 18, Color.WHITE);
            drawRight("SERIES " + state.wins[local] + " : " + state.wins[1 - local], 34, Color.WHITE);
            drawManager.drawBox(ox, oy, PvpMatch.WIDTH, PvpMatch.HEIGHT, Color.DARK_GRAY);
            for (int[] a : state.augments) {
                int ax = ox + PvpMatch.viewX(a[1], PvpMatch.AUGMENT_SIZE, local);
                int ay = oy + PvpMatch.viewY(a[2], PvpMatch.AUGMENT_SIZE, local);
                drawManager.fillBox(ax, ay, PvpMatch.AUGMENT_SIZE, PvpMatch.AUGMENT_SIZE, AUGMENT_COLORS[a[0]]);
                String letter = AUGMENT_LETTERS[a[0]];
                drawManager.drawSmallString(letter, ax + (PvpMatch.AUGMENT_SIZE
                        - drawManager.getSmallStringWidth(letter)) / 2, ay + 10, Color.BLACK);
            }
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
            if (augment) {
                drawManager.drawSmallString("YOU:" + effectNames(state.effects[local]), 12, oy + PvpMatch.HEIGHT + 16,
                        Color.GREEN);
                drawRight("OPP:" + effectNames(state.effects[1 - local]), oy + PvpMatch.HEIGHT + 16, Color.RED);
            }
            drawResult(state, local);
        } else drawManager.drawMenuRow(this, client.getStatus(), height / 2, false);
        drawManager.drawSmallString(augment ? "A/D move  SPACE fire  S teleport  R setup  ESC back"
                : "A/D move  SPACE fire  R setup  ESC back", 12, height - 32, Color.GRAY);
        drawManager.completeDrawing(this);
    }

    /** Series score between rounds and the final winner at the end. */
    private void drawResult(PvpProtocol.State state, int local) {
        String score = "YOU " + state.wins[local] + " : " + state.wins[1 - local] + " OPPONENT";
        if (state.phase == PvpMatch.FINISHED) {
            String result = state.winner < 0 ? "MATCH DRAW"
                    : state.winner == local ? "YOU WIN THE MATCH" : "YOU LOSE THE MATCH";
            drawManager.drawMenuRow(this, result, height / 2 - 12, true);
            drawManager.drawMenuRow(this, score, height / 2 + 16, false);
        } else if (client.isClosed()) {
            drawManager.drawMenuRow(this, client.getStatus(), height / 2, true);
        } else if (state.phase == PvpMatch.ROUND_OVER) {
            String result = state.winner < 0 ? "DRAW" : state.winner == local ? "YOU WIN" : "YOU LOSE";
            drawManager.drawMenuRow(this, "ROUND " + state.round + ": " + result, height / 2 - 24, true);
            drawManager.drawMenuRow(this, score, height / 2 + 4, false);
            drawManager.drawMenuRow(this, "Next round in " + (state.ticksLeft + 59) / 60, height / 2 + 30, false);
        }
    }
}
