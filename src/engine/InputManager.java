package engine;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

/**
 * Manages keyboard and mouse input for the provided screen.
 * 
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 * 
 */
public final class InputManager implements KeyListener, MouseMotionListener, MouseListener {

	/** Number of recognised keys. */
	private static final int NUM_KEYS = 256;
	/** Array with the keys marked as pressed or not. */
	private static boolean[] keys;
	/** Singleton instance of the class. */
	private static InputManager instance;
	/** Current mouse X position. */
	private volatile int mouseX;
	/** Current mouse Y position. */
	private volatile int mouseY;
	/** Whether the mouse has moved since the last check. */
	private boolean isMoved = false;
	/** Whether a mouse button is currently held down. */
	private boolean isClick;
	/** Whether the left mouse button is held down. */
	private volatile boolean leftMousePressed;
	/** Bounded text events, enabled only while an in-game form owns keyboard input. */
	private final java.util.concurrent.ArrayBlockingQueue<String> textEvents =
			new java.util.concurrent.ArrayBlockingQueue<String>(256);
	private boolean textInputEnabled;
	private final java.awt.KeyEventDispatcher textDispatcher = event -> {
		if (event.getID() == KeyEvent.KEY_PRESSED) {
			if (event.getKeyCode() == KeyEvent.VK_TAB)
				textEvents.offer(event.isShiftDown() ? "\u000b" : "\t");
			else if (event.getKeyCode() == KeyEvent.VK_ENTER) textEvents.offer("\n");
			else if (event.getKeyCode() == KeyEvent.VK_ESCAPE) textEvents.offer("\u001b");
			else if (event.isControlDown() && event.getKeyCode() == KeyEvent.VK_A)
				textEvents.offer("\u0001");
			else if (event.isControlDown() && event.getKeyCode() == KeyEvent.VK_V) {
				try {
					String text = (String) java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
							.getData(java.awt.datatransfer.DataFlavor.stringFlavor);
					textEvents.offer(text.substring(0, Math.min(256, text.length()))
							.replaceAll("[\\r\\n\\t\\x00-\\x1f]", ""));
				} catch (Exception ignored) { }
			}
		} else if (event.getID() == KeyEvent.KEY_TYPED) {
			char ch = event.getKeyChar();
			if (ch == '\b' || (ch >= 32 && ch <= 126)) textEvents.offer(String.valueOf(ch));
		}
		return true;
	};

	public void setTextInputEnabled(boolean enabled) {
		if (enabled == textInputEnabled) return;
		textInputEnabled = enabled;
		textEvents.clear();
		java.util.Arrays.fill(keys, false);
		java.awt.KeyboardFocusManager manager = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager();
		if (enabled) manager.addKeyEventDispatcher(textDispatcher);
		else manager.removeKeyEventDispatcher(textDispatcher);
	}

	public String pollTextInput() { return textEvents.poll(); }

	/**
	 * Private constructor.
	 */
	private InputManager() {
		keys = new boolean[NUM_KEYS];
	}

	/**
	 * Returns shared instance of InputManager.
	 * 
	 * @return Shared instance of InputManager.
	 */
	protected static InputManager getInstance() {
		if (instance == null)
			instance = new InputManager();
		return instance;
	}

	/**
	 * Returns true if the provided key is currently pressed.
	 * 
	 * @param keyCode
	 *            Key number to check.
	 * @return Key state.
	 */
	public boolean isKeyDown(final int keyCode) {
		return keys[keyCode];
	}

	/**
	 * Changes the state of the key to pressed.
	 * 
	 * @param key
	 *            Key pressed.
	 */
	@Override
	public void keyPressed(final KeyEvent key) {
		if (key.getKeyCode() >= 0 && key.getKeyCode() < NUM_KEYS)
			keys[key.getKeyCode()] = true;
	}

	/**
	 * Changes the state of the key to not pressed.
	 * 
	 * @param key
	 *            Key released.
	 */
	@Override
	public void keyReleased(final KeyEvent key) {
		if (key.getKeyCode() >= 0 && key.getKeyCode() < NUM_KEYS)
			keys[key.getKeyCode()] = false;
	}

	/**
	 * Does nothing.
	 * 
	 * @param key
	 *            Key typed.
	 */
	@Override
	public void keyTyped(final KeyEvent key) {

	}

	/**
	 * Returns the current mouse X position.
	 *
	 * @return Current mouse X position.
	 */
	public int getMouseX() {
		return mouseX;
	}

	/**
	 * Returns the current mouse Y position.
	 *
	 * @return Current mouse Y position.
	 */
	public int getMouseY() {
		return mouseY;
	}
	
	/**
	 * Returns whether a mouse button is currently held down.
	 *
	 * @return true if a mouse button is held down.
	 */
	public boolean isMousePressed() { 
		return isClick;
	}

	/**
	 * Updates the mouse position and records mouse movement.
	 *
	 * @param e
	 * 			Mouse movement event.
	 */
	@Override
	public void mouseMoved(final MouseEvent e) {
		mouseX = e.getX();
		mouseY = e.getY();
		isMoved = true;

	}

	/**
	 * Returns whether the mouse has moved since the last check
	 *
	 *  @return true if movement was recorded
	 */

	public boolean isMouseMoved() {
		boolean moved = isMoved;
		isMoved = false;
		return moved;
	}
	/**
	 * Updates the mouse position and records movement while dragging.
	 *
	 * @param e
	 *            Mouse drag event containing the current pointer position.
	 */
	@Override
	public void mouseDragged(final MouseEvent e) {
		mouseMoved(e);
	}
	/**
	 * Does nothing.
	 * 
	 * @param e Mouse event.
	 */
	@Override
	public void mouseClicked(final MouseEvent e) {
	}
	/**
	 * Marks the mouse button as pressed
	 * 
	 * @param e Mouse event containing button and position information.
	 */
	@Override
	public void mousePressed(final MouseEvent e) {
		mouseX = e.getX();
		mouseY = e.getY();
		isClick = true;
		if (e.getButton() == MouseEvent.BUTTON1)
			this.leftMousePressed = true;
	}
	/**
	 * Marks the mouse buttons as released
	 *
	 * @param e Mouse event containing button and position information.
	 */
	@Override
	public void mouseReleased(final MouseEvent e) {
		isClick = false;
		mouseX = e.getX();
		mouseY = e.getY();
		if (e.getButton() == MouseEvent.BUTTON1)
			this.leftMousePressed = false;
	}
	/**
	 * Does nothing.
	 * 
	 * @param e Mouse event.
	 *            
	 */
	@Override
	public void mouseEntered(final MouseEvent e) {

	}


	/**
	 * Clears the mouse button state when the mouse exits the component.
	 * 
	 * @param e Mouse event.
	 */
	@Override
	public void mouseExited(final MouseEvent e) {
		isClick = false;
		this.leftMousePressed = false;
	}

	/**
	 * Returns whether the left mouse button is currently held down.
	 *
	 * @return true if the left mouse button is held down.
	 */
	public boolean isLeftMousePressed() {
		return this.leftMousePressed;
	}
}
