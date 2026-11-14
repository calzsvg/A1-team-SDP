package pvp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Headless TCP server: pairs clients in arrival order, up to 16 arenas. */
public final class PvpServer implements AutoCloseable {
    private final ServerSocket listener;
    private final List<Room> rooms = new ArrayList<Room>();
    private volatile boolean closed;

    public PvpServer(String bind, int port) throws IOException {
        listener = new ServerSocket();
        listener.setReuseAddress(true);
        listener.bind(new InetSocketAddress(bind, port), 32);
    }
    public int getPort() { return listener.getLocalPort(); }

    public void serve() throws IOException {
        while (!closed) {
            Socket socket;
            try { socket = listener.accept(); }
            catch (IOException e) { if (closed) return; throw e; }
            try {
                socket.setSoTimeout(3000); socket.setTcpNoDelay(true);
                DataInputStream in = new DataInputStream(socket.getInputStream());
                if (in.readInt() != PvpProtocol.MAGIC || in.readInt() != PvpProtocol.VERSION)
                    throw new IOException("Protocol mismatch");
                socket.setSoTimeout(5000);
                synchronized (rooms) {
                    Room waiting = null;
                    for (Iterator<Room> it = rooms.iterator(); it.hasNext();) {
                        Room room = it.next();
                        if (room.closed) it.remove();
                        else if (room.canJoin()) waiting = room;
                    }
                    if (waiting == null) {
                        if (rooms.size() >= 16) throw new IOException("Server full");
                        waiting = new Room(); rooms.add(waiting);
                    }
                    try { waiting.join(socket, in); }
                    catch (IOException e) {
                        if (waiting.count == 0) waiting.close();
                        throw e;
                    }
                }
            } catch (IOException e) { socket.close(); }
        }
    }

    private static final class Peer {
        private final Socket socket;
        private final DataInputStream in;
        private final DataOutputStream out;
        private volatile boolean closed;
        private volatile int input;
        private volatile PvpProtocol.State latest;
        Peer(Socket socket, DataInputStream in, int player) throws IOException {
            this.socket = socket; this.in = in;
            out = new DataOutputStream(socket.getOutputStream());
            out.writeInt(PvpProtocol.MAGIC); out.writeInt(PvpProtocol.VERSION);
            out.writeInt(player); out.flush();
        }
        void start() {
            daemon("pvp-reader", new Runnable() {
                public void run() {
                    try {
                        while (!closed) {
                            int value = in.readUnsignedByte();
                            if ((value & ~15) != 0) throw new IOException("Invalid input");
                            input = value;
                        }
                    } catch (IOException e) { close(); }
                }
            });
            daemon("pvp-writer", new Runnable() {
                public void run() {
                    try {
                        while (!closed) {
                            PvpProtocol.State s = latest;
                            if (s != null) PvpProtocol.write(out, s);
                            Thread.sleep(33);
                        }
                    } catch (IOException e) { close(); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); close(); }
                }
            });
        }
        void close() {
            closed = true;
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    private static final class Room implements Runnable {
        private final Peer[] peers = new Peer[2];
        private final PvpMatch match = new PvpMatch();
        private volatile boolean closed;
        private int count;
        synchronized boolean canJoin() {
            return !closed && count == 1 && !peers[0].closed;
        }
        synchronized void join(Socket socket, DataInputStream in) throws IOException {
            if (closed || count == 2) throw new IOException("Room closed");
            Peer peer = new Peer(socket, in, count);
            peers[count++] = peer; peer.start();
            if (count == 1) daemon("pvp-arena", this);
        }
        public void run() {
            long started = System.nanoTime();
            try {
                while (!closed) {
                    long tickStart = System.nanoTime();
                    synchronized (this) {
                        for (int p = 0; p < count; p++) if (peers[p].closed) return;
                        // Waiting rooms expire after two minutes; full sessions after ten.
                        long age = tickStart - started;
                        if (age > 600000000000L
                                || (match.phase == PvpMatch.WAITING && age > 120000000000L)) return;
                        if (count == 2) match.tick(peers[0].input, peers[1].input);
                        PvpProtocol.State s = PvpProtocol.capture(match, count);
                        for (int p = 0; p < count; p++) peers[p].latest = s;
                    }
                    long remaining = 16666667L - (System.nanoTime() - tickStart);
                    if (remaining > 0) Thread.sleep(remaining / 1000000L, (int) (remaining % 1000000L));
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { close(); }
        }
        synchronized void close() {
            closed = true;
            for (int p = 0; p < count; p++) peers[p].close();
        }
    }
    private static void daemon(String name, Runnable action) {
        Thread thread = new Thread(action, name); thread.setDaemon(true); thread.start();
    }
    public void close() {
        closed = true;
        try { listener.close(); } catch (IOException ignored) { }
        synchronized (rooms) { for (Room room : rooms) room.close(); }
    }
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : PvpProtocol.DEFAULT_PORT;
        String bind = args.length > 1 ? args[1] : "0.0.0.0";
        final PvpServer server = new PvpServer(bind, port);
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() { server.close(); }
        }));
        System.out.println("PvP server listening on " + bind + ":" + server.getPort());
        server.serve();
    }
}
