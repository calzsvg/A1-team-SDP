package pvp;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Fixed-size authoritative arena played as a best-of-five series.
 * Input directions are relative to each player. The host (player 0) picks
 * Normal or Augment mode in the lobby; the mode is locked once the host is ready.
 */
public final class PvpMatch {
    public static final int WIDTH = 400, HEIGHT = 400;
    public static final int SHIP_W = 26, SHIP_H = 16;
    /** Input bits. AUGMENT is only read from the host while waiting; SKILL fires teleport. */
    public static final int LEFT = 1, RIGHT = 2, FIRE = 4, READY = 8, AUGMENT = 16, SKILL = 32;
    public static final int INPUT_MASK = 63;
    public static final int WAITING = 0, COUNTDOWN = 1, PLAYING = 2, FINISHED = 3, ROUND_OVER = 4;
    public static final int NORMAL = 0, AUGMENT_MODE = 1;
    public static final int ROUNDS = 5, WINS_NEEDED = 3;
    public static final int START_HP = 5, MAX_HP = 8;
    public static final int COUNTDOWN_TICKS = 180, ROUND_OVER_TICKS = 180;
    public static final int FIRE_COOLDOWN = 18, RAPID_COOLDOWN = 9, TELEPORT_COOLDOWN = 90;
    /** Augment kinds, also used as indices for the HUD labels. */
    public static final int TRIPLE_SHOT = 0, RAPID_FIRE = 1, EXTRA_LIFE = 2, TELEPORT = 3;
    public static final int AUGMENT_KINDS = 4, AUGMENT_SIZE = 12, MAX_AUGMENTS = 2;
    /** Effect flags that stay active until the end of the round. */
    public static final int EFFECT_TRIPLE = 1, EFFECT_RAPID = 2, EFFECT_TELEPORT = 4;
    public static final int FIRST_AUGMENT_TICKS = 180, AUGMENT_INTERVAL = 300, AUGMENT_LIFETIME = 480;

    public final int[] x = {187, 187};
    public final int[] y = {360, 24};
    public final int[] hp = {START_HP, START_HP};
    public final int[] wins = new int[2];
    public final int[] effects = new int[2];
    public final boolean[] ready = new boolean[2];
    public final List<Shot> shots = new ArrayList<Shot>();
    public final List<Augment> augments = new ArrayList<Augment>();
    private final int[] cooldown = new int[2], teleportCooldown = new int[2], previous = new int[2];
    private final Random random;
    private int augmentClock = FIRST_AUGMENT_TICKS, nextLane;
    public int phase = WAITING, ticksLeft = COUNTDOWN_TICKS, winner = -1;
    public int mode = NORMAL, round = 1;

    public static final class Shot {
        public final int owner, vx;
        public int x, y;
        public Shot(int owner, int x, int y) { this(owner, x, y, 0); }
        public Shot(int owner, int x, int y, int vx) {
            this.owner = owner; this.x = x; this.y = y; this.vx = vx;
        }
    }

    /** A pickup that drifts from the centre into one player's lane, then floats along it. */
    public static final class Augment {
        public final int kind;
        public int x, y, vx, vy, life = AUGMENT_LIFETIME;
        public Augment(int kind, int x, int y, int vx, int vy) {
            this.kind = kind; this.x = x; this.y = y; this.vx = vx; this.vy = vy;
        }
    }

    public PvpMatch() { this(new Random()); }
    public PvpMatch(Random random) { this.random = random; }

    public void tick(int input0, int input1) {
        int[] inputs = {input0, input1};
        if (phase == FINISHED) return;
        if (phase == WAITING) {
            for (int p = 0; p < 2; p++) ready[p] = (inputs[p] & READY) != 0;
            // The host chooses the mode; it is locked as soon as the host is ready.
            if (!ready[0]) mode = (input0 & AUGMENT) != 0 ? AUGMENT_MODE : NORMAL;
            if (ready[0] && ready[1]) phase = COUNTDOWN;
            return;
        }
        if (phase == COUNTDOWN) {
            if (--ticksLeft == 0) phase = PLAYING;
            return;
        }
        if (phase == ROUND_OVER) {
            if (--ticksLeft == 0) startRound();
            return;
        }
        for (int p = 0; p < 2; p++) {
            int direction = ((inputs[p] & RIGHT) != 0 ? 1 : 0)
                    - ((inputs[p] & LEFT) != 0 ? 1 : 0);
            if (p == 1) direction = -direction;
            x[p] = Math.max(0, Math.min(WIDTH - SHIP_W, x[p] + direction * 4));
            if (cooldown[p] > 0) cooldown[p]--;
            if (teleportCooldown[p] > 0) teleportCooldown[p]--;
            boolean skillPressed = (inputs[p] & SKILL) != 0 && (previous[p] & SKILL) == 0;
            if (skillPressed && (effects[p] & EFFECT_TELEPORT) != 0 && teleportCooldown[p] == 0) {
                // Teleport mirrors the ship to the other side of the arena.
                x[p] = WIDTH - SHIP_W - x[p];
                teleportCooldown[p] = TELEPORT_COOLDOWN;
            }
            if ((inputs[p] & FIRE) != 0 && cooldown[p] == 0) fire(p);
            previous[p] = inputs[p];
        }
        if (mode == AUGMENT_MODE) updateAugments();
        int[] damage = new int[2];
        for (Iterator<Shot> it = shots.iterator(); it.hasNext();) {
            Shot shot = it.next();
            shot.y += shot.owner == 0 ? -6 : 6;
            shot.x += shot.vx;
            int target = 1 - shot.owner;
            if (shot.x < x[target] + SHIP_W && shot.x + 4 > x[target]
                    && shot.y < y[target] + SHIP_H && shot.y + 8 > y[target]) {
                damage[target]++;
                it.remove();
            } else if (shot.y < -8 || shot.y > HEIGHT || shot.x < 0 || shot.x > WIDTH - 4)
                it.remove();
        }
        for (int p = 0; p < 2; p++) hp[p] = Math.max(0, hp[p] - damage[p]);
        if (hp[0] <= 0 || hp[1] <= 0) endRound(hp[0] <= 0 && hp[1] <= 0 ? -1 : hp[0] > 0 ? 0 : 1);
    }

    private void fire(int p) {
        int sx = x[p] + SHIP_W / 2 - 2, sy = p == 0 ? y[p] - 8 : y[p] + SHIP_H;
        shots.add(new Shot(p, sx, sy));
        if ((effects[p] & EFFECT_TRIPLE) != 0) {
            shots.add(new Shot(p, sx, sy, -1));
            shots.add(new Shot(p, sx, sy, 1));
        }
        cooldown[p] = (effects[p] & EFFECT_RAPID) != 0 ? RAPID_COOLDOWN : FIRE_COOLDOWN;
    }

    private void updateAugments() {
        if (--augmentClock <= 0) {
            augmentClock = AUGMENT_INTERVAL;
            if (augments.size() < MAX_AUGMENTS) {
                // Lanes alternate so both players get the same number of chances.
                int lane = nextLane; nextLane = 1 - nextLane;
                augments.add(new Augment(random.nextInt(AUGMENT_KINDS),
                        24 + random.nextInt(WIDTH - 48 - AUGMENT_SIZE), (HEIGHT - AUGMENT_SIZE) / 2,
                        random.nextBoolean() ? 2 : -2, lane == 0 ? 2 : -2));
            }
        }
        for (Iterator<Augment> it = augments.iterator(); it.hasNext();) {
            Augment a = it.next();
            int laneY = laneY(a.vy > 0 ? 0 : 1);
            if (a.y != laneY) {
                a.y += a.vy;
                if (a.vy > 0 ? a.y > laneY : a.y < laneY) a.y = laneY;
            }
            a.x += a.vx;
            if (a.x <= 0 || a.x >= WIDTH - AUGMENT_SIZE) {
                a.vx = -a.vx; a.x = Math.max(0, Math.min(WIDTH - AUGMENT_SIZE, a.x));
            }
            int collector = -1;
            for (int p = 0; p < 2 && collector < 0; p++)
                if (a.x < x[p] + SHIP_W && a.x + AUGMENT_SIZE > x[p]
                        && a.y < y[p] + SHIP_H && a.y + AUGMENT_SIZE > y[p]) collector = p;
            if (collector >= 0) { apply(collector, a.kind); it.remove(); }
            else if (--a.life <= 0) it.remove();
        }
    }

    /** Top of an augment floating through the centre of a player's lane. */
    private int laneY(int player) {
        return y[player] + (SHIP_H - AUGMENT_SIZE) / 2;
    }

    /** Grants an augment; effects last until the round ends. */
    public void apply(int player, int kind) {
        switch (kind) {
        case TRIPLE_SHOT: effects[player] |= EFFECT_TRIPLE; break;
        case RAPID_FIRE: effects[player] |= EFFECT_RAPID; break;
        case EXTRA_LIFE: hp[player] = Math.min(MAX_HP, hp[player] + 1); break;
        default: effects[player] |= EFFECT_TELEPORT; break;
        }
    }

    private void endRound(int roundWinner) {
        winner = roundWinner;
        if (roundWinner >= 0) wins[roundWinner]++;
        boolean decided = wins[0] >= WINS_NEEDED || wins[1] >= WINS_NEEDED || round >= ROUNDS;
        if (decided) {
            phase = FINISHED;
            winner = wins[0] == wins[1] ? -1 : wins[0] > wins[1] ? 0 : 1;
        } else {
            phase = ROUND_OVER;
            ticksLeft = ROUND_OVER_TICKS;
        }
    }

    /** Resets the arena for the next round; augments and their effects never carry over. */
    private void startRound() {
        round++;
        winner = -1;
        for (int p = 0; p < 2; p++) {
            x[p] = 187; hp[p] = START_HP; effects[p] = 0;
            cooldown[p] = teleportCooldown[p] = 0;
        }
        shots.clear(); augments.clear();
        augmentClock = FIRST_AUGMENT_TICKS;
        phase = PLAYING;
    }

    /** Rectangle transform, also used by the renderer for bullets. */
    public static int viewX(int x, int width, int player) {
        return player == 0 ? x : WIDTH - x - width;
    }
    public static int viewY(int y, int height, int player) {
        return player == 0 ? y : HEIGHT - y - height;
    }
}
