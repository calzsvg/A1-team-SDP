package pvp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Small versioned binary protocol; never deserializes Java objects. */
public final class PvpProtocol {
    public static final int MAGIC = 0x41315056, VERSION = 1, DEFAULT_PORT = 7777;
    private PvpProtocol() { }

    /** A fresh instance is published per packet, never mutated by the reader. */
    public static final class State {
        public int phase, ticksLeft, winner, players;
        public final int[] x = new int[2], y = new int[2], hp = new int[2];
        public final boolean[] ready = new boolean[2];
        public int[][] shots;
    }

    public static State capture(PvpMatch match, int players) {
        State s = new State();
        s.phase = match.phase; s.ticksLeft = match.ticksLeft;
        s.winner = match.winner; s.players = players;
        for (int p = 0; p < 2; p++) {
            s.x[p] = match.x[p]; s.y[p] = match.y[p];
            s.hp[p] = match.hp[p]; s.ready[p] = match.ready[p];
        }
        s.shots = new int[match.shots.size()][3];
        for (int i = 0; i < s.shots.length; i++) {
            PvpMatch.Shot shot = match.shots.get(i);
            s.shots[i] = new int[] {shot.owner, shot.x, shot.y};
        }
        return s;
    }

    public static void write(DataOutputStream out, State s) throws IOException {
        out.writeInt(s.phase); out.writeInt(s.ticksLeft);
        out.writeInt(s.winner); out.writeInt(s.players);
        for (int p = 0; p < 2; p++) {
            out.writeInt(s.x[p]); out.writeInt(s.y[p]);
            out.writeInt(s.hp[p]); out.writeBoolean(s.ready[p]);
        }
        out.writeInt(s.shots.length);
        for (int[] shot : s.shots) for (int value : shot) out.writeInt(value);
        out.flush();
    }

    public static State read(DataInputStream in) throws IOException {
        State s = new State();
        s.phase = in.readInt(); s.ticksLeft = in.readInt();
        s.winner = in.readInt(); s.players = in.readInt();
        if (s.phase < 0 || s.phase > 3 || s.players < 1 || s.players > 2
                || s.ticksLeft < 0 || s.ticksLeft > 180 || s.winner < -1 || s.winner > 1)
            throw new IOException("Invalid arena state");
        for (int p = 0; p < 2; p++) {
            s.x[p] = in.readInt(); s.y[p] = in.readInt();
            s.hp[p] = in.readInt(); s.ready[p] = in.readBoolean();
            if (s.x[p] < 0 || s.x[p] > PvpMatch.WIDTH - PvpMatch.SHIP_W
                    || s.y[p] < 0 || s.y[p] > PvpMatch.HEIGHT - PvpMatch.SHIP_H
                    || s.hp[p] < 0 || s.hp[p] > 5) throw new IOException("Invalid ship");
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
        return s;
    }
}
