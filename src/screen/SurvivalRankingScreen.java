package screen;

import java.awt.Color;
import java.awt.event.KeyEvent;
import engine.DrawManager;
import engine.SurvivalRanking;

/** Shows the local Survival ranking; reached from the game modes menu. */
public final class SurvivalRankingScreen extends Screen {
    /** Code of the game modes screen, where Escape goes back to. */
    private static final int GAME_MODES = 11;
    private final SurvivalRanking ranking;

    public SurvivalRankingScreen(int width, int height, int fps) {
        super(width, height, fps);
        returnCode = GAME_MODES;
        ranking = SurvivalRanking.local();
    }
    @Override public int run() { super.run(); return returnCode; }
    @Override protected void update() {
        super.update();
        if (inputDelay.checkFinished()
                && (inputManager.isKeyDown(KeyEvent.VK_ESCAPE) || inputManager.isKeyDown(KeyEvent.VK_ENTER)))
            isRunning = false;
        drawManager.initDrawing(this);
        drawManager.drawCenteredBigString(this, "SURVIVAL RANKING", 70, Color.GREEN);
        drawTable(this, drawManager, ranking, -1, 105);
        drawManager.drawKeyHints(this, "esc back");
        drawManager.completeDrawing(this);
    }
    /** "mm:ss" for a number of seconds. */
    static String clock(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
    /**
     * Draws the ranking table starting at the given y; the row at {@code highlight} (zero-based, or -1 for none)
     * is drawn in yellow with a marker so the player can find their own run.
     */
    static void drawTable(Screen screen, DrawManager dm, SurvivalRanking ranking, int highlight, int top) {
        int[] columns = { 40, 90, 215, 310 };
        String[] heads = { "#", "SCORE", "TIME", "LEVEL" };
        for (int c = 0; c < heads.length; c++) dm.drawSmallString(heads[c], columns[c], top, Color.GRAY);
        dm.fillBox(24, top + 6, screen.getWidth() - 48, 1, Color.GREEN);
        if (ranking.entries().isEmpty()) {
            String none = "No runs yet";
            dm.drawSmallString(none, (screen.getWidth() - dm.getSmallStringWidth(none)) / 2, top + 50, Color.GRAY);
            return;
        }
        for (int i = 0; i < ranking.entries().size(); i++) {
            SurvivalRanking.Entry entry = ranking.entries().get(i);
            Color color = i == highlight ? Color.YELLOW : Color.WHITE;
            int y = top + 26 + i * 22;
            if (i == highlight) dm.drawSmallString(">", 24, y, color);
            dm.drawSmallString(String.valueOf(i + 1), columns[0], y, color);
            dm.drawSmallString(String.valueOf(entry.score), columns[1], y, color);
            dm.drawSmallString(clock(entry.seconds), columns[2], y, color);
            dm.drawSmallString(String.valueOf(entry.level), columns[3], y, color);
        }
    }
}
