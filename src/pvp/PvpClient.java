package pvp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Background network I/O keeps connect/read/write off the game thread. */
public final class PvpClient implements AutoCloseable {
    private volatile Socket socket;
    private volatile boolean closed;
    private volatile int input;
    private volatile int player;
    private volatile PvpProtocol.State state;
    private volatile String status = "Connecting...";

    public PvpClient(final String host, final int port, final String roomCode) {
        if (roomCode == null || !roomCode.matches("[0-9]{3}"))
            throw new IllegalArgumentException("Connection number must be exactly 3 digits.");
        Thread reader = new Thread(new Runnable() {
            public void run() {
                try {
                    DataInputStream in = connect(host, port, roomCode);
                    if (closed) return;
                    final DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    status = "Connected";
                    Thread writer = new Thread(new Runnable() {
                        public void run() {
                            try {
                                while (!closed) {
                                    out.writeByte(input); out.flush(); Thread.sleep(33);
                                }
                            } catch (IOException e) { fail(); }
                            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        }
                    }, "pvp-input");
                    writer.setDaemon(true); writer.start();
                    while (!closed) state = PvpProtocol.read(in);
                } catch (Rejected e) { status = e.getMessage(); close(); }
                catch (IOException e) { fail(); }
                catch (IllegalArgumentException e) { fail(); }
            }
        }, "pvp-state");
        reader.setDaemon(true); reader.start();
    }

    /** Only connect to the endpoint entered by the player. */
    private DataInputStream connect(String host, int port, String roomCode) throws IOException {
            Socket attempt = new Socket();
            synchronized (this) {
                if (closed) { attempt.close(); throw new IOException("Closed"); }
                socket = attempt;
            }
            try {
                attempt.connect(new InetSocketAddress(host, port), 4000);
                attempt.setTcpNoDelay(true); attempt.setSoTimeout(5000);
                DataOutputStream out = new DataOutputStream(attempt.getOutputStream());
                DataInputStream in = new DataInputStream(attempt.getInputStream());
                out.writeInt(PvpProtocol.MAGIC); out.writeInt(PvpProtocol.VERSION); out.flush();
                out.writeInt(Integer.parseInt(roomCode)); out.flush();
                if (in.readInt() != PvpProtocol.MAGIC || in.readInt() != PvpProtocol.VERSION)
                    throw new IOException("Server version mismatch");
                int response = in.readInt();
                if (response != PvpProtocol.OK) {
                    String message = response == PvpProtocol.ROOM_FULL ? "Room full. Use another number."
                            : response == PvpProtocol.SERVER_FULL ? "Server full. Try again later."
                            : "Server rejected the connection number.";
                    throw new Rejected(message);
                }
                player = in.readInt();
                if (player < 0 || player > 1) throw new IOException("Invalid player slot");
                state = PvpProtocol.read(in);
                return in;
            } catch (IOException | IllegalArgumentException e) { attempt.close(); throw e; }
    }
    private static final class Rejected extends IOException {
        private static final long serialVersionUID = 1L;
        Rejected(String message) { super(message); }
    }
    private void fail() {
        if (!closed) status = state == null ? "Connection failed / server unavailable"
                : "Disconnected / opponent left";
        close();
    }
    public void setInput(int value) { input = value; }
    public int getPlayer() { return player; }
    public PvpProtocol.State getState() { return state; }
    public String getStatus() { return status; }
    public boolean isClosed() { return closed; }
    public synchronized void close() {
        closed = true;
        try { if (socket != null) socket.close(); } catch (IOException ignored) { }
    }
}
