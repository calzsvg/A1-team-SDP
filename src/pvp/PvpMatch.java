package pvp;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Fixed-size authoritative arena. Input directions are relative to each player. */
public final class PvpMatch {
    public static final int WIDTH = 400, HEIGHT = 400;
    public static final int SHIP_W = 26, SHIP_H = 16;
    public static final int LEFT = 1, RIGHT = 2, FIRE = 4, READY = 8;
    public static final int WAITING = 0, COUNTDOWN = 1, PLAYING = 2, FINISHED = 3;
    public final int[] x = {187, 187};
    public final int[] y = {360, 24};
    public final int[] hp = {5, 5};
    public final boolean[] ready = new boolean[2];
    public final List<Shot> shots = new ArrayList<Shot>();
    private final int[] cooldown = new int[2];
    public int phase = WAITING, ticksLeft = 180, winner = -1;

    public static final class Shot {
        public final int owner, x;
        public int y;
        public Shot(int owner, int x, int y) {
            this.owner = owner; this.x = x; this.y = y;
        }
    }

    public void tick(int input0, int input1) {
        int[] inputs = {input0, input1};
        if (phase == FINISHED) return;
        if (phase == WAITING) {
            for (int p = 0; p < 2; p++) ready[p] = (inputs[p] & READY) != 0;
            if (ready[0] && ready[1]) phase = COUNTDOWN;
            return;
        }
        if (phase == COUNTDOWN) {
            if (--ticksLeft == 0) phase = PLAYING;
            return;
        }
        for (int p = 0; p < 2; p++) {
            int direction = ((inputs[p] & RIGHT) != 0 ? 1 : 0)
                    - ((inputs[p] & LEFT) != 0 ? 1 : 0);
            if (p == 1) direction = -direction;
            x[p] = Math.max(0, Math.min(WIDTH - SHIP_W, x[p] + direction * 4));
            if (cooldown[p] > 0) cooldown[p]--;
            if ((inputs[p] & FIRE) != 0 && cooldown[p] == 0) {
                shots.add(new Shot(p, x[p] + SHIP_W / 2 - 2,
                        p == 0 ? y[p] - 8 : y[p] + SHIP_H));
                cooldown[p] = 18;
            }
        }
        for (Iterator<Shot> it = shots.iterator(); it.hasNext();) {
            Shot shot = it.next();
            shot.y += shot.owner == 0 ? -6 : 6;
            int target = 1 - shot.owner;
            if (shot.x < x[target] + SHIP_W && shot.x + 4 > x[target]
                    && shot.y < y[target] + SHIP_H && shot.y + 8 > y[target]) {
                hp[target]--;
                it.remove();
            } else if (shot.y < -8 || shot.y > HEIGHT) it.remove();
        }
        if (hp[0] <= 0 || hp[1] <= 0) {
            phase = FINISHED;
            winner = hp[0] <= 0 && hp[1] <= 0 ? -1 : hp[0] > 0 ? 0 : 1;
        }
    }

    /** Rectangle transform, also used by the renderer for bullets. */
    public static int viewX(int x, int width, int player) {
        return player == 0 ? x : WIDTH - x - width;
    }
    public static int viewY(int y, int height, int player) {
        return player == 0 ? y : HEIGHT - y - height;
    }
}
