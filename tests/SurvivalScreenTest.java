import engine.Core;
import engine.DrawManager;
import engine.Frame;
import engine.SurvivalGame;
import engine.PurchaseManager;
import entity.Ship.ShipType;
import shop.ShapeSkin;
import shop.Skin;
import shop.ShopCatalog;
import shop.ShopCategory;
import screen.SurvivalScreen;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.imageio.ImageIO;

/** Windowed rendering/input smoke check; writes previews under bin/. */
public final class SurvivalScreenTest {
    private static final Method UPDATE;
    private static final Field GAME;
    static {
        try {
            UPDATE = SurvivalScreen.class.getDeclaredMethod("update"); UPDATE.setAccessible(true);
            GAME = SurvivalScreen.class.getDeclaredField("game"); GAME.setAccessible(true);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private static void key(Frame frame, int key, boolean down) {
        KeyEvent event = new KeyEvent(frame, down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(), 0, key, KeyEvent.CHAR_UNDEFINED);
        if (down) Core.getInputManager().keyPressed(event);
        else Core.getInputManager().keyReleased(event);
    }
    private static void tap(Frame frame, SurvivalScreen screen, int key) throws Exception {
        key(frame, key, true); UPDATE.invoke(screen);
        key(frame, key, false); UPDATE.invoke(screen);
    }
    private static void capture(String name) throws Exception {
        Field buffer = DrawManager.class.getDeclaredField("backBuffer"); buffer.setAccessible(true);
        ImageIO.write((BufferedImage) buffer.get(null), "png", new File("bin/" + name + ".png"));
    }
    /** Simulate loadouts in memory only; never purchase or write the user's saves. */
    @SuppressWarnings("unchecked")
    private static void checkLoadouts(Frame frame) throws Exception {
        PurchaseManager purchases = PurchaseManager.getInstance();
        Field ownedField = PurchaseManager.class.getDeclaredField("owned"); ownedField.setAccessible(true);
        Field selectedField = PurchaseManager.class.getDeclaredField("selectedShip"); selectedField.setAccessible(true);
        Field shapesField = PurchaseManager.class.getDeclaredField("selectedShapes"); shapesField.setAccessible(true);
        Field skinField = PurchaseManager.class.getDeclaredField("selectedSkin"); skinField.setAccessible(true);
        java.util.Map<String, Integer> owned = (java.util.Map<String, Integer>) ownedField.get(purchases);
        java.util.Map<ShipType, ShapeSkin> shapes = (java.util.Map<ShipType, ShapeSkin>) shapesField.get(purchases);
        java.util.Map<String, Integer> originalOwned = new java.util.HashMap<>(owned);
        java.util.Map<ShipType, ShapeSkin> originalShapes = new java.util.HashMap<>(shapes);
        Object originalShip = selectedField.get(purchases), originalSkin = skinField.get(purchases);
        Field spriteField = SurvivalScreen.class.getDeclaredField("shipSprite"); spriteField.setAccessible(true);
        Field screenSkin = SurvivalScreen.class.getDeclaredField("skin"); screenSkin.setAccessible(true);
        try {
            for (ShipType type : ShipType.values()) {
                if (type != ShipType.STANDARD)
                    owned.put(ShopCatalog.findByRef(ShopCategory.SHIP, type.name()).getId(), 1);
                selectedField.set(purchases, type);
                shapes.remove(type);
                SurvivalScreen screen = new SurvivalScreen(frame.getWidth(), frame.getHeight(), 60);
                if (((SurvivalGame) GAME.get(screen)).shipType != type
                        || spriteField.get(screen) != type.getIdleSprite())
                    throw new AssertionError("Equipped ship must reach Survival: " + type);
                UPDATE.invoke(screen);
            }
            ShapeSkin shape = ShapeSkin.BIG_BULLET_CANNON;
            owned.put(ShopCatalog.findByRef(ShopCategory.SHAPE, shape.name()).getId(), 1);
            shapes.put(ShipType.BIG_BULLET, shape);
            selectedField.set(purchases, ShipType.BIG_BULLET);
            Skin cosmetic = java.util.Arrays.stream(Skin.values()).filter(s -> s != Skin.CLASSIC).findFirst().get();
            owned.put(ShopCatalog.findByRef(ShopCategory.SKIN, cosmetic.name()).getId(), 1);
            skinField.set(purchases, cosmetic);
            SurvivalScreen screen = new SurvivalScreen(frame.getWidth(), frame.getHeight(), 60);
            if (spriteField.get(screen) != shape.getSprite() || screenSkin.get(screen) != cosmetic)
                throw new AssertionError("Equipped shape and color skin must reach Survival");
            Thread.sleep(1100); UPDATE.invoke(screen); capture("survival-equipped-cannon");
            SurvivalGame old = (SurvivalGame) GAME.get(screen);
            old.damage = 999; old.hp = 0;
            tap(frame, screen, KeyEvent.VK_R);
            SurvivalGame fresh = (SurvivalGame) GAME.get(screen);
            if (fresh == old || fresh.shipType != ShipType.BIG_BULLET || fresh.damage != 20)
                throw new AssertionError("Retry must retain equipped ship and reset run upgrades");
            owned.remove(ShopCatalog.findByRef(ShopCategory.SHIP, ShipType.BIG_BULLET.name()).getId());
            screen = new SurvivalScreen(frame.getWidth(), frame.getHeight(), 60);
            if (((SurvivalGame) GAME.get(screen)).shipType != ShipType.STANDARD)
                throw new AssertionError("Locked ships must fall back to Standard");
        } finally {
            owned.clear(); owned.putAll(originalOwned);
            shapes.clear(); shapes.putAll(originalShapes);
            selectedField.set(purchases, originalShip); skinField.set(purchases, originalSkin);
        }
    }
    public static void main(String[] args) throws Exception {
        Frame frame = new Frame(448, 520);
        try {
            Core.getDrawManager().setFrame(frame);
            SurvivalScreen screen = new SurvivalScreen(frame.getWidth(), frame.getHeight(), 60);
            Thread.sleep(1100);
            SurvivalGame game = (SurvivalGame) GAME.get(screen);
            for (int i = 0; i < 240; i++) { Thread.sleep(16); UPDATE.invoke(screen); }
            capture("survival-play");
            tap(frame, screen, KeyEvent.VK_P);
            double clock = game.elapsed;
            Thread.sleep(100); UPDATE.invoke(screen);
            if (game.elapsed != clock) throw new AssertionError("Pause must freeze combat");
            tap(frame, screen, KeyEvent.VK_P);
            game.addExperience(20); UPDATE.invoke(screen); capture("survival-upgrade");
            tap(frame, screen, KeyEvent.VK_2);
            if (!game.choices().isEmpty()) throw new AssertionError("Number key must select upgrade");
            game.hp = 0; UPDATE.invoke(screen); capture("survival-results");
            tap(frame, screen, KeyEvent.VK_R);
            SurvivalGame fresh = (SurvivalGame) GAME.get(screen);
            if (fresh == game || fresh.hp != 100 || fresh.level != 1)
                throw new AssertionError("Retry must reset the run");
            checkLoadouts(frame);
            System.out.println("SurvivalScreenTest: rendering, pause, upgrade input, results and retry passed");
        } finally { frame.dispose(); }
        System.exit(0);
    }
}
