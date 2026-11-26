package pvp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Small versioned binary protocol; never deserializes Java objects. */
public final class PvpProtocol {
    /** Version 3 adds the game mode, best-of-five series and augments. */
    public static final int MAGIC = 0x41315056, VERSION = 3, DEFAULT_PORT = 7777;
    public static final int OK = 0, ROOM_FULL = 1, SERVER_FULL = 2, INVALID_ROOM = 3;
    private PvpProtocol() { }

    /** A fresh instance is published per packet, never mutated by the reader. */
    public static final class State {
        public int phase, ticksLeft, winner, players, mode, round;
        public final int[] x = new int[2], y = new int[2], hp = new int[2];
        public final int[] wins = new int[2], effects = new int[2];
        public final boolean[] ready = new boolean[2];
        /** Each shot is {owner, x, y}; each augment is {kind, x, y}. */
        public int[][] shots, augments;
    }

    public static State capture(PvpMatch match, int players) {
        State s = new State();
        s.phase = match.phase; s.ticksLeft = match.ticksLeft;
        s.winner = match.winner; s.players = players;
        s.mode = match.mode; s.round = match.round;
        for (int p = 0; p < 2; p++) {
            s.x[p] = match.x[p]; s.y[p] = match.y[p];
            s.hp[p] = match.hp[p]; s.ready[p] = match.ready[p];
            s.wins[p] = match.wins[p]; s.effects[p] = match.effects[p];
        }
        s.shots = new int[match.shots.size()][3];
        for (int i = 0; i < s.shots.length; i++) {
            PvpMatch.Shot shot = match.shots.get(i);
            s.shots[i] = new int[] {shot.owner, shot.x, shot.y};
        }
        s.augments = new int[match.augments.size()][3];
        for (int i = 0; i < s.augments.length; i++) {
            PvpMatch.Augment a = match.augments.get(i);
            s.augments[i] = new int[] {a.kind, a.x, a.y};
        }
        return s;
    }

    public static void write(DataOutputStream out, State s) throws IOException {
        out.writeInt(s.phase); out.writeInt(s.ticksLeft);
        out.writeInt(s.winner); out.writeInt(s.players);
        out.writeInt(s.mode); out.writeInt(s.round);
        for (int p = 0; p < 2; p++) {
            out.writeInt(s.x[p]); out.writeInt(s.y[p]);
            out.writeInt(s.hp[p]); out.writeBoolean(s.ready[p]);
            out.writeInt(s.wins[p]); out.writeInt(s.effects[p]);
        }
        out.writeInt(s.shots.length);
        for (int[] shot : s.shots) for (int value : shot) out.writeInt(value);
        out.writeInt(s.augments.length);
        for (int[] augment : s.augments) for (int value : augment) out.writeInt(value);
        out.flush();
    }

    public static State read(DataInputStream in) throws IOException {
        State s = new State();
        s.phase = in.readInt(); s.ticksLeft = in.readInt();
        s.winner = in.readInt(); s.players = in.readInt();
        s.mode = in.readInt(); s.round = in.readInt();
        if (s.phase < 0 || s.phase > 4 || s.players < 1 || s.players > 2
                || s.ticksLeft < 0 || s.ticksLeft > 180 || s.winner < -1 || s.winner > 1
                || s.mode < 0 || s.mode > 1 || s.round < 1 || s.round > PvpMatch.ROUNDS)
            throw new IOException("Invalid arena state");
        for (int p = 0; p < 2; p++) {
            s.x[p] = in.readInt(); s.y[p] = in.readInt();
            s.hp[p] = in.readInt(); s.ready[p] = in.readBoolean();
            s.wins[p] = in.readInt(); s.effects[p] = in.readInt();
            if (s.x[p] < 0 || s.x[p] > PvpMatch.WIDTH - PvpMatch.SHIP_W
                    || s.y[p] < 0 || s.y[p] > PvpMatch.HEIGHT - PvpMatch.SHIP_H
                    || s.hp[p] < 0 || s.hp[p] > PvpMatch.MAX_HP
                    || s.wins[p] < 0 || s.wins[p] > PvpMatch.WINS_NEEDED
                    || s.effects[p] < 0 || s.effects[p] > 7) throw new IOException("Invalid ship");
        }
        int count = in.readInt();
        if (count < 0 || count > 128) throw new IOException("Invalid bullet count");
        s.shots = new int[count][3];
        for (int[] shot : s.shots) {
            for (int i = 0; i < 3; i++) shot[i] = in.readInt();
            if (shot[0] < 0 || shot[0] > 1 || shot[1] < 0 || shot[1] > PvpMatch.WIDTH
                    || shot[2] < -8 || shot[2] > PvpMatch.HEIGHT)
                throw new IOException("Invalid bullet");
        }
        count = in.readInt();
        if (count < 0 || count > PvpMatch.MAX_AUGMENTS) throw new IOException("Invalid augment count");
        s.augments = new int[count][3];
        for (int[] augment : s.augments) {
            for (int i = 0; i < 3; i++) augment[i] = in.readInt();
            if (augment[0] < 0 || augment[0] >= PvpMatch.AUGMENT_KINDS
                    || augment[1] < 0 || augment[1] > PvpMatch.WIDTH - PvpMatch.AUGMENT_SIZE
                    || augment[2] < 0 || augment[2] > PvpMatch.HEIGHT - PvpMatch.AUGMENT_SIZE)
                throw new IOException("Invalid augment");
        }
        return s;
    }
}
