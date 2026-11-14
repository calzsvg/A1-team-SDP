package screen;

import java.awt.event.KeyEvent;

import engine.Cooldown;
import engine.Core;
import engine.soundEngine.MenuSounds;
import engine.soundEngine.SoundManager;

/** Lets the player choose a game mode from the main menu. */
public final class GameModesScreen extends Screen {
	private static final String[] MODES = { "Classic", "PvP", "Survival" };
	private static final int[] CODES = { 2, 8, 10 };
	private final Cooldown selectionCooldown;
	private int selected;
	private boolean confirmWasDown;
	private boolean moveWasDown;

	public GameModesScreen(final int width, final int height, final int fps) {
		super(width, height, fps);
		this.returnCode = 1;
		this.selectionCooldown = Core.getCooldown(200);
		this.selectionCooldown.reset();
		this.confirmWasDown = inputManager.isKeyDown(KeyEvent.VK_ENTER)
				|| inputManager.isKeyDown(KeyEvent.VK_SPACE);
	}

	@Override
	public int run() {
		super.run();
		return this.returnCode;
	}

	@Override
	protected void update() {
		super.update();
		boolean confirmDown = inputManager.isKeyDown(KeyEvent.VK_ENTER)
				|| inputManager.isKeyDown(KeyEvent.VK_SPACE);
		boolean confirmPressed = confirmDown && !confirmWasDown;
		confirmWasDown = confirmDown;
		boolean up = inputManager.isKeyDown(KeyEvent.VK_UP)
				|| inputManager.isKeyDown(KeyEvent.VK_W);
		boolean down = inputManager.isKeyDown(KeyEvent.VK_DOWN)
				|| inputManager.isKeyDown(KeyEvent.VK_S);
		boolean moving = up || down;

		if (inputDelay.checkFinished()) {
			if (inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
				isRunning = false;
			} else if (selectionCooldown.checkFinished() && up != down) {
				selected = (selected + (up ? MODES.length - 1 : 1))
						% MODES.length;
				if (!moveWasDown)
					MenuSounds.playMove();
				selectionCooldown.reset();
			} else if (confirmPressed) {
				returnCode = CODES[selected];
				if (returnCode == 2)
					SoundManager.getInstance().stopBgm();
				isRunning = false;
			}
		}
		moveWasDown = moving;
		drawManager.initDrawing(this);
		drawManager.drawCenteredBigString(this, "GAME MODES", height / 3);
		for (int i = 0; i < MODES.length; i++)
			drawManager.drawMenuRow(this, MODES[i], height / 2 + 35 * i,
					i == selected);
		drawManager.drawKeyHints(this,
				"w+s/arrows move, space select, esc back");
		drawManager.completeDrawing(this);
	}
}
