package screen;

import java.awt.event.KeyEvent;

/** Placeholder until Survival gameplay is implemented. */
public final class SurvivalScreen extends Screen {
	public SurvivalScreen(final int width, final int height, final int fps) {
		super(width, height, fps);
		this.returnCode = 11;
	}

	@Override
	public int run() {
		super.run();
		return this.returnCode;
	}

	@Override
	protected void update() {
		super.update();
		if (this.inputDelay.checkFinished()
				&& this.inputManager.isKeyDown(KeyEvent.VK_ESCAPE))
			this.isRunning = false;

		this.drawManager.initDrawing(this);
		this.drawManager.drawCenteredBigString(this, "SURVIVAL",
				this.height / 3);
		this.drawManager.drawCenteredRegularString(this,
				"Coming soon", this.height / 2);
		this.drawManager.drawKeyHints(this, "esc back to menu");
		this.drawManager.completeDrawing(this);
	}
}
