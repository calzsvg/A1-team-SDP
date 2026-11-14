package screen;

import java.awt.Color;
import pvp.PvpClient;
import pvp.PvpConnectionSettings;
import pvp.PvpConnectionForm;

/** Connection form and session owner for the setup -> lobby -> match flow. */
public final class OnlinePvpScreen extends Screen {
    /** Shared geometry for drawing and pointer hit-testing. */
    private static final int FORM_WIDTH = 336;
    private static final int SERVER_TOP = 194, SERVER_HEIGHT = 38;
    private static final int NUMBER_TOP = 266, NUMBER_HEIGHT = 40;
    private static final int DIGIT_WIDTH = 42, DIGIT_STEP = 50;
    private static final int NUMBER_WIDTH = 2 * DIGIT_STEP + DIGIT_WIDTH;
    private static final int CONNECT_OFFSET = 180, CONNECT_WIDTH = FORM_WIDTH - CONNECT_OFFSET;
    private PvpClient client;
    private final Starfield stars;
    private PvpConnectionSettings settings;
    private final PvpConnectionForm form = new PvpConnectionForm();
    private boolean mouseWasDown;

    public OnlinePvpScreen(int width, int height, int fps) {
        super(width, height, fps);
        returnCode = 1;
        stars = new Starfield(width, height);
    }
    public void initialize() { inputManager.setTextInputEnabled(true); }
    private void connect() {
        PvpConnectionSettings chosen = form.submit();
        if (chosen == null) return;
        settings = chosen;
        inputManager.setTextInputEnabled(false);
        client = new PvpClient(settings.host, settings.port, settings.roomCode);
        isRunning = false;
    }
    public int run() {
        try {
            while (true) {
                inputManager.setTextInputEnabled(true);
                inputDelay.reset();
                super.run();
                inputManager.setTextInputEnabled(false);
                if (client == null) return returnCode;

                PvpLobbyScreen lobby = new PvpLobbyScreen(width, height, fps, client, settings.roomCode);
                lobby.initialize();
                int next = lobby.run();
                if (next == PvpLobbyScreen.PVP_GAME_CODE) {
                    PvpMatchScreen match = new PvpMatchScreen(width, height, fps, client, settings.roomCode);
                    match.initialize();
                    next = match.run();
                }
                client.close(); client = null;
                if (next != PvpLobbyScreen.SETUP_CODE) return returnCode;
                form.error = ""; form.focus(0);
                mouseWasDown = inputManager.isLeftMousePressed();
            }
        }
        finally {
            inputManager.setTextInputEnabled(false);
            if (client != null) client.close();
        }
    }
    protected void update() {
        updateForm();
        stars.update(); draw();
    }

    private void updateForm() {
        String text;
        while ((text = inputManager.pollTextInput()) != null) {
            for (char ch : text.toCharArray()) {
                if (ch == 27) { isRunning = false; return; }
                if (ch == '\n') {
                    if (inputDelay.checkFinished()) { connect(); if (client != null) return; }
                }
                else form.type(ch);
            }
        }
        boolean mouse = inputManager.isLeftMousePressed();
        if (mouse && !mouseWasDown && inputDelay.checkFinished()) {
            int x = inputManager.getMouseX(), y = inputManager.getMouseY();
            int left = (width - FORM_WIDTH) / 2;
            if (contains(x, y, left, SERVER_TOP, FORM_WIDTH, SERVER_HEIGHT)) form.focus(0);
            else if (contains(x, y, left, NUMBER_TOP, NUMBER_WIDTH, NUMBER_HEIGHT)) form.focus(1);
            else if (contains(x, y, left + CONNECT_OFFSET, NUMBER_TOP, CONNECT_WIDTH, NUMBER_HEIGHT)) {
                form.focus(2); connect();
            }
        }
        mouseWasDown = mouse;
    }

    private static boolean contains(int x, int y, int left, int top, int width, int height) {
        return x >= left && x <= left + width && y >= top && y <= top + height;
    }

    private void drawForm() {
        final int left = (width - FORM_WIDTH) / 2;
        final Color muted = new Color(130, 148, 140), panel = new Color(9, 19, 16);
        drawManager.drawMenuRow(this, "ONLINE PVP", 105, true);
        drawManager.drawSmallString("CONNECT WITH A FRIEND", left, 140, muted);
        drawManager.drawSmallString("SERVER ADDRESS", left, SERVER_TOP - 13, form.focus == 0 ? Color.GREEN : muted);
        drawManager.fillBox(left, SERVER_TOP, FORM_WIDTH, SERVER_HEIGHT, panel);
        drawManager.drawBox(left, SERVER_TOP, FORM_WIDTH, SERVER_HEIGHT, form.focus == 0 ? Color.GREEN : Color.DARK_GRAY);
        String visible = form.address;
        while (drawManager.getRegularStringWidth(visible + "_") > FORM_WIDTH - 24 && !visible.isEmpty())
            visible = visible.substring(1);
        if (form.selected && form.focus == 0)
            drawManager.fillBox(left + 8, SERVER_TOP + 6, FORM_WIDTH - 16, SERVER_HEIGHT - 13, new Color(26, 62, 40));
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        drawManager.drawRegularString(visible.isEmpty() ? "name or IP:port" : visible,
                left + 10, SERVER_TOP + 26, visible.isEmpty() ? muted : Color.WHITE);
        if (form.focus == 0 && blink)
            drawManager.fillBox(left + 10 + (visible.isEmpty() ? 0 : drawManager.getRegularStringWidth(visible)),
                    SERVER_TOP + 8, 2, SERVER_HEIGHT - 18, Color.GREEN);
        drawManager.drawSmallString("CONNECTION NUMBER", left, NUMBER_TOP - 13, form.focus == 1 ? Color.GREEN : muted);
        for (int i = 0; i < 3; i++) {
            int bx = left + i * DIGIT_STEP;
            drawManager.fillBox(bx, NUMBER_TOP, DIGIT_WIDTH, NUMBER_HEIGHT, form.selected && form.focus == 1
                    ? new Color(26, 62, 40) : panel);
            drawManager.drawBox(bx, NUMBER_TOP, DIGIT_WIDTH, NUMBER_HEIGHT, form.focus == 1 ? Color.GREEN : Color.DARK_GRAY);
            String digit = i < form.code.length() ? form.code.substring(i, i + 1)
                    : form.focus == 1 && blink && i == form.code.length() ? "_" : "-";
            drawManager.drawRegularString(digit, bx + (DIGIT_WIDTH - drawManager.getRegularStringWidth(digit)) / 2,
                    NUMBER_TOP + 27, i < form.code.length() ? Color.WHITE : muted);
        }
        drawManager.fillBox(left + CONNECT_OFFSET, NUMBER_TOP, CONNECT_WIDTH, NUMBER_HEIGHT,
                form.focus == 2 ? new Color(18, 49, 27) : panel);
        drawManager.drawBox(left + CONNECT_OFFSET, NUMBER_TOP, CONNECT_WIDTH, NUMBER_HEIGHT, Color.GREEN);
        String connectLabel = form.focus == 2 ? "> CONNECT <" : "CONNECT";
        drawManager.drawRegularString(connectLabel, left + CONNECT_OFFSET + CONNECT_WIDTH / 2
                - drawManager.getRegularStringWidth(connectLabel) / 2, NUMBER_TOP + 27, Color.GREEN);
        drawManager.drawSmallString("USE THE SAME NUMBER AS YOUR FRIEND", left, 335, muted);
        drawManager.drawSmallString("Room numbers are not passwords.", left, 353, muted);
        if (!form.error.isEmpty()) {
            String line = ""; int y = 386;
            for (String word : form.error.split(" ")) {
                if (drawManager.getSmallStringWidth(line + word) > 330) {
                    drawManager.drawSmallString(line, left, y, Color.RED); y += 15; line = "";
                }
                line += word + " ";
            }
            drawManager.drawSmallString(line, left, y, Color.RED);
        }
        drawManager.drawSmallString("TAB switch  ENTER connect  ESC back", left, height - 32, muted);
    }
    private void draw() {
        drawManager.initDrawing(this);
        drawManager.drawStarfield(this, stars);
        drawForm();
        drawManager.completeDrawing(this);
    }
}
