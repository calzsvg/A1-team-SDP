package pvp;

/** Keyboard editing state for the game-rendered connection form. */
public final class PvpConnectionForm {
    public String address = "", code = "", error = "";
    public int focus;
    public boolean selected;

    public void focus(int field) { focus = Math.floorMod(field, 3); selected = false; }
    public void type(char ch) {
        if (ch == '\t') { focus(focus + 1); return; }
        if (ch == '\u000b') { focus(focus - 1); return; }
        if (ch == '\u0001') { selected = true; return; }
        if (focus == 2) return;
        String value = focus == 0 ? address : code;
        if (ch == '\b') value = selected ? "" : value.substring(0, Math.max(0, value.length() - 1));
        else if (ch > 32 && ch < 127 && (focus == 0 || ch >= '0' && ch <= '9')) {
            if (selected) value = "";
            int max = focus == 0 ? 253 : 3;
            if (value.length() < max) value += ch;
        } else return;
        selected = false; error = "";
        if (focus == 0) address = value; else code = value;
    }
    public PvpConnectionSettings submit() {
        try { return PvpConnectionSettings.parse(address, code); }
        catch (IllegalArgumentException e) { error = e.getMessage(); return null; }
    }
}
