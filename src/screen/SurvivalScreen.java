package screen;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.Random;
import engine.SurvivalGame;
import engine.PurchaseManager;
import engine.DrawManager.SpriteType;
import entity.Ship.ShipType;
import shop.ShapeSkin;
import shop.Skin;
import shop.ShopCatalog;

/** Top-down auto-fire survival, with run-local progression. */
public final class SurvivalScreen extends Screen {
    private SurvivalGame game;
    private final ShipType shipType;
    private final SpriteType shipSprite;
    private final Skin skin;
    private long previousTime = System.nanoTime();
    private final boolean[] held = new boolean[256];
    private boolean paused;
    private int selected;

    public SurvivalScreen(int width, int height, int fps) {
        super(width, height, fps);
        returnCode = 11;
        PurchaseManager purchases = PurchaseManager.getInstance();
        shipType = purchases.getSelectedShip();
        ShapeSkin shape = purchases.getSelectedShape(shipType);
        shipSprite = shape == null ? shipType.getIdleSprite() : shape.getSprite();
        skin = purchases.getSelectedSkin();
        game = new SurvivalGame(width, height, new Random(), shipType);
        for (int i = 0; i < held.length; i++) held[i] = inputManager.isKeyDown(i);
    }
    @Override public int run() { super.run(); return returnCode; }
    private boolean down(int key) { return inputManager.isKeyDown(key); }
    private boolean pressed(int key) { return down(key) && !held[key]; }
    @Override protected void update() {
        long now = System.nanoTime();
        double dt = (now - previousTime) / 1_000_000_000.0;
        previousTime = now;
        if (inputDelay.checkFinished()) {
            if (pressed(KeyEvent.VK_ESCAPE)) isRunning = false;
            else if (game.isOver()) {
                if (pressed(KeyEvent.VK_R)) {
                    game = new SurvivalGame(width, height, new Random(), shipType);
                    paused = false;
                    selected = 0;
                }
            } else if (!game.choices().isEmpty()) {
                if (pressed(KeyEvent.VK_UP) || pressed(KeyEvent.VK_W)) selected = (selected + 2) % 3;
                if (pressed(KeyEvent.VK_DOWN) || pressed(KeyEvent.VK_S)) selected = (selected + 1) % 3;
                int choice = -1;
                for (int i = 0; i < 3; i++) if (pressed(KeyEvent.VK_1 + i)) choice = i;
                if (pressed(KeyEvent.VK_ENTER) || pressed(KeyEvent.VK_SPACE)) choice = selected;
                if (choice >= 0) { game.choose(choice); selected = 0; }
            } else {
                if (pressed(KeyEvent.VK_P)) paused = !paused;
                if (!paused) game.update(dt,
                        (down(KeyEvent.VK_D) || down(KeyEvent.VK_RIGHT) ? 1 : 0)
                        - (down(KeyEvent.VK_A) || down(KeyEvent.VK_LEFT) ? 1 : 0),
                        (down(KeyEvent.VK_S) || down(KeyEvent.VK_DOWN) ? 1 : 0)
                        - (down(KeyEvent.VK_W) || down(KeyEvent.VK_UP) ? 1 : 0));
            }
        }
        for (int i = 0; i < held.length; i++) held[i] = down(i);
        drawManager.initDrawing(this);
        draw();
        drawManager.completeDrawing(this);
    }
    private void centered(String value, int y, Color color) {
        drawManager.drawCenteredRegularString(this, value, y, color);
    }
    private void title(String value, int y) {
        drawManager.drawCenteredBigString(this, value, y, Color.GREEN);
    }
    private void smallCentered(String value, int y, Color color) {
        drawManager.drawSmallString(value,
                (width - drawManager.getSmallStringWidth(value)) / 2, y, color);
    }
    private void bar(int x, int y, int w, double ratio, Color color) {
        drawManager.fillBox(x, y, w, 6, Color.BLACK);
        drawManager.drawBox(x, y, w, 6, color);
        int filled = (int) ((w - 2) * Math.max(0, Math.min(1, ratio)));
        if (filled > 0) drawManager.fillBox(x + 1, y + 1, filled, 5, color);
    }
    private void drawEnemy(SurvivalGame.Enemy enemy) {
        SpriteType sprite;
        Color color;
        switch (enemy.kind) {
        case SurvivalGame.ARMORED: sprite = SpriteType.EnemyShipB1; color = new Color(230, 140, 65); break;
        case SurvivalGame.SHOOTER: sprite = SpriteType.EnemyShipC1; color = new Color(204, 120, 250); break;
        case SurvivalGame.RUSHER: sprite = SpriteType.EnemyShipA2; color = new Color(255, 235, 90); break;
        case SurvivalGame.SPLITTER: sprite = SpriteType.EnemyShipB2; color = new Color(90, 210, 230); break;
        case SurvivalGame.MINI: sprite = SpriteType.EnemyShipA1; color = new Color(150, 230, 255); break;
        case SurvivalGame.ELITE: sprite = SpriteType.EnemyShipC2; color = new Color(255, 70, 70); break;
        default: sprite = SpriteType.EnemyShipA1; color = new Color(250, 92, 110); break;
        }
        if (enemy.kind == SurvivalGame.ELITE)
            drawManager.drawSpriteScaled(sprite, (int) enemy.x - 18, (int) enemy.y - 12, 3, color);
        else if (enemy.kind == SurvivalGame.MINI)
            drawManager.drawSpriteScaled(sprite, (int) enemy.x - 8, (int) enemy.y - 6, 1, color);
        else
            drawManager.drawSprite(sprite, (int) enemy.x - 12, (int) enemy.y - 10, color);
    }
    private void drawPickup(SurvivalGame.Pickup pickup) {
        Color color; String letter;
        switch (pickup.type) {
        case SHIELD: color = new Color(90, 210, 230); letter = "S"; break;
        case MAGNET: color = new Color(255, 235, 90); letter = "M"; break;
        case BOMB: color = new Color(255, 70, 70); letter = "B"; break;
        case DOUBLE_XP: color = new Color(230, 140, 65); letter = "X"; break;
        default: color = Color.WHITE; letter = "R"; break;
        }
        int px = (int) pickup.x, py = (int) pickup.y;
        drawManager.fillBox(px - 7, py - 7, 14, 14, Color.BLACK);
        drawManager.drawBox(px - 7, py - 7, 14, 14, color);
        drawManager.drawSmallString(letter, px - drawManager.getSmallStringWidth(letter) / 2, py + 4, color);
    }
    /** Remaining time of active pickup effects, shown under the HUD. */
    private void drawBuffs() {
        StringBuilder sb = new StringBuilder();
        if (game.shield > 0) sb.append(String.format("SHIELD %.0f  ", game.shield));
        if (game.magnet > 0) sb.append(String.format("MAGNET %.0f  ", game.magnet));
        if (game.doubleXp > 0) sb.append(String.format("2X XP %.0f  ", game.doubleXp));
        if (game.rapid > 0) sb.append(String.format("RAPID %.0f", game.rapid));
        if (sb.length() > 0) drawManager.drawSmallString(sb.toString().trim(), 12, 99, Color.WHITE);
    }
    private void draw() {
        // Classic's black playfield, white bullet sprites and green separators.
        for (SurvivalGame.Enemy enemy : game.enemies) {
            drawEnemy(enemy);
            if (enemy.hp < enemy.maxHp) {
                int half = enemy.kind == SurvivalGame.ELITE ? 20 : 14;
                bar((int) enemy.x - half, (int) enemy.y - (half + 3), half * 2, enemy.hp / enemy.maxHp, Color.GREEN);
            }
        }
        for (SurvivalGame.Shot shot : game.shots) {
            drawManager.drawSpriteScaled(shot.hostile ? SpriteType.EnemyBullet : SpriteType.Bullet,
                    (int) shot.x - shot.width / 2, (int) shot.y - shot.height / 2,
                    shot.width / 3, Color.WHITE);
        }
        for (SurvivalGame.Repair repair : game.repairs) {
            drawManager.fillBox((int) repair.x - 6, (int) repair.y - 2, 12, 4, Color.GREEN);
            drawManager.fillBox((int) repair.x - 2, (int) repair.y - 6, 4, 12, Color.GREEN);
        }
        for (SurvivalGame.Pickup pickup : game.pickups) drawPickup(pickup);
        drawManager.drawSprite(shipSprite, (int) game.x - 13, (int) game.y - 8,
                game.hurtFlash > 0 ? Color.WHITE : skin.colorAt(System.currentTimeMillis()));
        drawManager.drawHorizontalLine(this, height - 28);
        drawHud();
        drawBuffs();

        if (game.isOver()) {
            panel(100, height - 170);
            title("GAME OVER", 155);
            centered("SURVIVAL RESULTS", 193, Color.WHITE);
            centered("LEVEL " + game.level, 237, Color.WHITE);
            centered("SHIPS DESTROYED " + game.kills, 267, Color.WHITE);
            centered("TIME " + time(), 297, Color.WHITE);
            drawManager.drawMenuRow(this, "R Retry", 340, true);
            drawManager.drawKeyHints(this, "r retry, esc back to modes");
        } else if (!game.choices().isEmpty()) {
            panel(100, height - 170);
            title("LEVEL UP", 135);
            centered("LEVEL " + game.level + " - CHOOSE AN UPGRADE", 163, Color.WHITE);
            for (int i = 0; i < 3; i++) {
                int baseline = 205 + i * 61;
                SurvivalGame.Upgrade upgrade = game.choices().get(i);
                drawManager.drawMenuRow(this, (i + 1) + " " + upgrade.title, baseline, i == selected);
                smallCentered(upgrade.description, baseline + 21, Color.GRAY);
            }
            drawManager.drawKeyHints(this, "1/2/3 select, w+s/arrows move, enter confirm");
        } else if (paused) {
            drawManager.drawPauseMenu(this, new String[] { "P Resume", "ESC Mode menu" }, 0);
            drawManager.drawKeyHints(this, "p resume, esc back to modes");
        } else {
            smallCentered("wasd/arrows move, auto fire, p pause, esc back", height - 10, Color.GRAY);
            if (game.elapsed < 6) {
                smallCentered("Destroy enemies before they reach the bottom", 106, Color.WHITE);
                smallCentered("Kills grant XP. Collect + drops to repair HP.", 123, Color.GRAY);
            }
        }
    }
    private String time() {
        return String.format("%02d:%02d", (int) game.elapsed / 60, (int) game.elapsed % 60);
    }
    private void drawHud() {
        drawManager.fillBox(0, 0, width, 85, Color.BLACK);
        drawManager.drawRegularString("HP", 12, 24, Color.WHITE);
        drawManager.drawRegularString((int) Math.ceil(game.hp) + "/" + (int) game.maxHp,
                45, 24, game.hurtFlash > 0 ? Color.RED : Color.GREEN);
        String summary = "LV " + game.level + "  TIME " + time();
        drawManager.drawRegularString(summary,
                width - 12 - drawManager.getRegularStringWidth(summary), 24, Color.WHITE);
        drawManager.drawHorizontalLine(this, 39);
        bar(12, 31, 115, game.hp / game.maxHp, Color.GREEN);
        drawManager.drawSmallString("XP " + game.xp + "/" + game.xpRequired(), 12, 54, Color.WHITE);
        bar(130, 47, width - 142, (double) game.xp / game.xpRequired(), Color.GREEN);
        smallCentered(ShopCatalog.shipName(shipType) + "  " + String.format("DMG %.0f  RATE %.1f  SHOTS %d  KILLS %d",
                game.damage, 1 / game.fireInterval, game.projectiles, game.kills), 68, Color.GRAY);
        smallCentered(String.format("LIFESTEAL %.0f%%  REPAIR %.1f HP/S",
                game.lifesteal * 100, game.regeneration), 81, Color.GRAY);
    }
    private void panel(int top, int panelHeight) {
        int panelWidth = width - 32;
        drawManager.fillBox(16, top, panelWidth, panelHeight, Color.BLACK);
        drawManager.drawBox(16, top, panelWidth, panelHeight, Color.GREEN);
    }
}
