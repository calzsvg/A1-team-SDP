package screen;

import java.awt.Color;
import java.awt.event.KeyEvent;

import engine.Core;
import engine.Cooldown;
import engine.DrawManager.SpriteType;
import engine.soundEngine.MenuSounds;

/**
 * Lobby before a local PvP match. Both players share one keyboard: each
 * presses their own fire key to toggle ready, and the match starts after a
 * short countdown once both are ready. Esc returns to the main menu.
 *
 * Player 2 is drawn at the top and Player 1 at the bottom, matching where
 * their ships will be in the match.
 */
public class PvpLobbyScreen extends Screen {

	/** Code returned to Core to start the PvP match (PvpScreen). */
	public static final int PVP_GAME_CODE = 9;
	/** Code returned to Core to go back to the main menu. */
	private static final int MENU_CODE = 1;
	/** Milliseconds between both players being ready and the match. */
	private static final int COUNTDOWN_TIME = 3000;

	/** Ready key of Player 1. */
	private static final int P1_READY_KEY = KeyEvent.VK_SPACE;
	/** Ready key of Player 2. */
	private static final int P2_READY_KEY = KeyEvent.VK_ENTER;

	/** Whether Player 1 is ready. */
	private boolean p1Ready;
	/** Whether Player 2 is ready. */
	private boolean p2Ready;
	/** Whether each key was held during the previous update. */
	private boolean p1KeyWasDown;
	private boolean p2KeyWasDown;
	private boolean escapeWasDown;
	/** Runs while both players are ready; null when it has not started. */
	private Cooldown countdown;
	/** Moment the countdown started, used to show the seconds left. */
	private long countdownStart;
	/** Stars behind the lobby. */
	private final Starfield starfield;

	/**
	 * Constructor, establishes the properties of the screen.
	 *
	 * @param width
	 *            Screen width.
	 * @param height
	 *            Screen height.
	 * @param fps
	 *            Frames per second, frame rate at which the game is run.
	 */
	public PvpLobbyScreen(final int width, final int height, final int fps) {
		super(width, height, fps);

		this.returnCode = MENU_CODE;
		this.starfield = new Starfield(width, height);
		// The key used to pick PvP in the menu may still be held, so it
		// must not count as a press here.
		this.p1KeyWasDown = this.inputManager.isKeyDown(P1_READY_KEY);
		this.p2KeyWasDown = this.inputManager.isKeyDown(P2_READY_KEY);
		this.escapeWasDown = this.inputManager.isKeyDown(KeyEvent.VK_ESCAPE);
	}

	/**
	 * Starts the action.
	 *
	 * @return Next screen code.
	 */
	public final int run() {
		super.run();

		return this.returnCode;
	}

	/**
	 * Updates the elements on screen and checks for events.
	 */
	protected final void update() {
		super.update();

		final boolean p1Pressed = pressed(P1_READY_KEY, this.p1KeyWasDown);
		final boolean p2Pressed = pressed(P2_READY_KEY, this.p2KeyWasDown);
		final boolean escapePressed =
				pressed(KeyEvent.VK_ESCAPE, this.escapeWasDown);
		this.p1KeyWasDown = this.inputManager.isKeyDown(P1_READY_KEY);
		this.p2KeyWasDown = this.inputManager.isKeyDown(P2_READY_KEY);
		this.escapeWasDown = this.inputManager.isKeyDown(KeyEvent.VK_ESCAPE);

		this.starfield.update();
		draw();

		if (!this.inputDelay.checkFinished())
			return;

		if (escapePressed) {
			this.returnCode = MENU_CODE;
			this.isRunning = false;
			return;
		}

		if (p1Pressed) {
			this.p1Ready = !this.p1Ready;
			MenuSounds.playMove();
		}
		if (p2Pressed) {
			this.p2Ready = !this.p2Ready;
			MenuSounds.playMove();
		}

		updateCountdown();
	}

	/**
	 * Starts the countdown when both players are ready, cancels it when one
	 * of them is not, and starts the match when it ends.
	 */
	private void updateCountdown() {
		if (!bothReady()) {
			this.countdown = null;
			return;
		}
		if (this.countdown == null) {
			this.countdown = Core.getCooldown(COUNTDOWN_TIME);
			this.countdown.reset();
			this.countdownStart = System.currentTimeMillis();
			return;
		}
		if (this.countdown.checkFinished()) {
			this.returnCode = PVP_GAME_CODE;
			this.isRunning = false;
		}
	}

	/**
	 * Checks whether a key went down since the previous update.
	 *
	 * @param keyCode
	 *            Key to check.
	 * @param wasDown
	 *            Whether the key was held during the previous update.
	 * @return True only on the update the key is pressed.
	 */
	private boolean pressed(final int keyCode, final boolean wasDown) {
		return this.inputManager.isKeyDown(keyCode) && !wasDown;
	}

	/**
	 * Checks whether both players are ready.
	 *
	 * @return True if both are ready.
	 */
	public final boolean bothReady() {
		return this.p1Ready && this.p2Ready;
	}

	/**
	 * Seconds left before the match starts, rounded up.
	 *
	 * @return Seconds left, or 0 if the countdown has not started.
	 */
	private int secondsLeft() {
		if (this.countdown == null)
			return 0;
		long elapsed = System.currentTimeMillis() - this.countdownStart;
		return (int) Math.max(1, (COUNTDOWN_TIME - elapsed + 999) / 1000);
	}

	/**
	 * Draws the elements associated with the screen.
	 */
	private void draw() {
		drawManager.initDrawing(this);

		drawManager.drawStarfield(this, this.starfield);
		drawManager.drawScreenTitle(this, "PvP");

		// Player 2 on top, Player 1 at the bottom, like in the match.
		drawPlayerPanel("Player 2", "<- -> move, enter fire", "enter",
				this.p2Ready, Color.RED, this.height * 2 / 9);

		drawManager.drawMenuRow(this, "VS", this.height / 2, false);

		drawPlayerPanel("Player 1", "a d move, space fire", "space",
				this.p1Ready, Color.GREEN, this.height * 3 / 5);

		if (bothReady())
			drawManager.drawMenuRow(this, "Starting in " + secondsLeft(),
					this.height * 7 / 8, true);

		drawManager.drawKeyHints(this,
				"space p1 ready, enter p2 ready, esc back");

		drawManager.completeDrawing(this);
	}

	/**
	 * Draws one player's name, ship, keys and ready state.
	 *
	 * @param name
	 *            Name of the player.
	 * @param keys
	 *            Keys the player uses in the match.
	 * @param readyKey
	 *            Name of the key that toggles ready.
	 * @param ready
	 *            Whether the player is ready.
	 * @param color
	 *            Colour of the player's ship.
	 * @param top
	 *            Height of the top of the panel.
	 */
	private void drawPlayerPanel(final String name, final String keys,
			final String readyKey, final boolean ready, final Color color,
			final int top) {
		final int row = 24;
		drawManager.drawMenuRow(this, name, top, false);
		// Ships are 13x8 pixels drawn at twice the size.
		drawManager.drawSprite(SpriteType.Ship, this.width / 2 - 13,
				top + row / 2, color);
		drawManager.drawMenuRow(this, keys, top + row * 2 + 4, false, false);
		if (ready)
			drawManager.drawMenuRow(this, "Ready", top + row * 3 + 8, true);
		else
			drawManager.drawMenuRow(this, "Press " + readyKey + " to ready",
					top + row * 3 + 8, false);
	}
}
