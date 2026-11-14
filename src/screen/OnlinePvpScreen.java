package screen;

import java.awt.Color;
import java.awt.event.KeyEvent;
import pvp.PvpClient;
import pvp.PvpMatch;
import pvp.PvpProtocol;
import pvp.PvpConnectionSettings;
import pvp.PvpConnectionForm;

/** Each client controls one ship and always sees it at the bottom. */
public final class OnlinePvpScreen extends Screen {
    private PvpClient client;
    private final Starfield stars;
    private boolean ready, readyWasDown, retryWasDown;
    private PvpConnectionSettings settings;
    private final PvpConnectionForm form = new PvpConnectionForm();
    private boolean editing = true, mouseWasDown;

    public OnlinePvpScreen(int width, int height, int fps) {
        super(width, height, fps);
        returnCode = 1;
        stars = new Starfield(width, height);
        readyWasDown = inputManager.isKeyDown(KeyEvent.VK_SPACE);
    }
    public void initialize() { inputManager.setTextInputEnabled(true); }
    private void connect() {
        PvpConnectionSettings chosen = form.submit();
        if (chosen == null) return;
        settings = chosen;
        inputManager.setTextInputEnabled(false);
        editing = false;
        ready = false;
        readyWasDown = inputManager.isKeyDown(KeyEvent.VK_SPACE);
        client = new PvpClient(settings.host, settings.port, settings.roomCode);
        inputDelay.reset();
    }
    public int run() {
        try { super.run(); return returnCode; }
        finally {
            inputManager.setTextInputEnabled(false);
            if (client != null) client.close();
        }
    }
    protected void update() {
        if (editing) { updateForm(); stars.update(); draw(); return; }
        boolean space = inputManager.isKeyDown(KeyEvent.VK_SPACE);
        boolean retry = inputManager.isKeyDown(KeyEvent.VK_R);
        if (inputDelay.checkFinished()) {
            if (inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) { isRunning = false; return; }
            if (retry && !retryWasDown && client != null
                    && (client.isClosed() || (client.getState() != null
                    && client.getState().phase == PvpMatch.FINISHED))) {
                client.close(); client = null; editing = true;
                form.error = ""; form.focus(0);
                inputManager.setTextInputEnabled(true);
                stars.update(); draw(); return;
            }
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

    private void updateForm() {
        String text;
        while ((text = inputManager.pollTextInput()) != null) {
            for (char ch : text.toCharArray()) {
                if (ch == 27) { isRunning = false; return; }
                if (ch == '\n') {
                    if (inputDelay.checkFinished()) { connect(); if (!editing) return; }
                }
                else form.type(ch);
            }
        }
        boolean mouse = inputManager.isLeftMousePressed();
        if (mouse && !mouseWasDown && inputDelay.checkFinished()) {
            int x = inputManager.getMouseX(), y = inputManager.getMouseY();
            int left = (width - 336) / 2;
            if (x >= left && x <= left + 336 && y >= 194 && y <= 232) form.focus(0);
            else if (x >= left && x <= left + 144 && y >= 266 && y <= 306) form.focus(1);
            else if (x >= left + 180 && x <= left + 336 && y >= 266 && y <= 306) {
                form.focus(2); connect();
            }
        }
        mouseWasDown = mouse;
    }

    private void drawForm() {
        final int left = (width - 336) / 2;
        final Color muted = new Color(130, 148, 140), panel = new Color(9, 19, 16);
        drawManager.drawMenuRow(this, "ONLINE PVP", 105, true);
        drawManager.drawSmallString("CONNECT WITH A FRIEND", left, 140, muted);
        drawManager.drawSmallString("SERVER ADDRESS", left, 181, form.focus == 0 ? Color.GREEN : muted);
        drawManager.fillBox(left, 194, 336, 38, panel);
        drawManager.drawBox(left, 194, 336, 38, form.focus == 0 ? Color.GREEN : Color.DARK_GRAY);
        String visible = form.address;
        while (drawManager.getRegularStringWidth(visible + "_") > 312 && !visible.isEmpty())
            visible = visible.substring(1);
        if (form.selected && form.focus == 0)
            drawManager.fillBox(left + 8, 200, 320, 25, new Color(26, 62, 40));
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        drawManager.drawRegularString(visible.isEmpty() ? "name or IP:port" : visible,
                left + 10, 220, visible.isEmpty() ? muted : Color.WHITE);
        if (form.focus == 0 && blink)
            drawManager.fillBox(left + 10 + (visible.isEmpty() ? 0 : drawManager.getRegularStringWidth(visible)),
                    202, 2, 20, Color.GREEN);
        drawManager.drawSmallString("CONNECTION NUMBER", left, 253, form.focus == 1 ? Color.GREEN : muted);
        for (int i = 0; i < 3; i++) {
            int bx = left + i * 50;
            drawManager.fillBox(bx, 266, 42, 40, form.selected && form.focus == 1
                    ? new Color(26, 62, 40) : panel);
            drawManager.drawBox(bx, 266, 42, 40, form.focus == 1 ? Color.GREEN : Color.DARK_GRAY);
            String digit = i < form.code.length() ? form.code.substring(i, i + 1)
                    : form.focus == 1 && blink && i == form.code.length() ? "_" : "-";
            drawManager.drawRegularString(digit, bx + 15, 293, i < form.code.length() ? Color.WHITE : muted);
        }
        drawManager.fillBox(left + 180, 266, 156, 40, form.focus == 2 ? new Color(18, 49, 27) : panel);
        drawManager.drawBox(left + 180, 266, 156, 40, Color.GREEN);
        String connectLabel = form.focus == 2 ? "> CONNECT <" : "CONNECT";
        drawManager.drawRegularString(connectLabel, left + 258
                - drawManager.getRegularStringWidth(connectLabel) / 2, 293, Color.GREEN);
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
        if (editing) { drawForm(); drawManager.completeDrawing(this); return; }
        drawManager.drawSmallString("ONLINE PVP", 12, 18, Color.GREEN);
        if (settings != null) drawManager.drawSmallString("ROOM " + settings.roomCode, 12, 34, Color.GRAY);
        PvpProtocol.State s = client == null ? null : client.getState();
        if (s == null) {
            drawManager.drawMenuRow(this, client == null ? "Enter server and connection number" : client.getStatus(),
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
        drawManager.drawSmallString("A/D move  SPACE fire/ready  R setup  ESC back",
                12, height - 32, Color.GRAY);
        drawManager.completeDrawing(this);
    }
}
