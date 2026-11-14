package pvp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Background network I/O keeps connect/read/write off the game thread. */
public final class PvpClient implements AutoCloseable {
    private final Socket socket = new Socket();
    private volatile boolean closed;
    private volatile int input;
    private volatile int player;
    private volatile PvpProtocol.State state;
    private volatile String status = "Connecting...";

    public PvpClient(final String host, final int port) {
        Thread reader = new Thread(new Runnable() {
            public void run() {
                try {
                    socket.connect(new InetSocketAddress(host, port), 4000);
                    if (closed) { socket.close(); return; }
                    socket.setTcpNoDelay(true);
                    socket.setSoTimeout(5000);
                    final DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    out.writeInt(PvpProtocol.MAGIC); out.writeInt(PvpProtocol.VERSION); out.flush();
                    if (in.readInt() != PvpProtocol.MAGIC || in.readInt() != PvpProtocol.VERSION)
                        throw new IOException("Server version mismatch");
                    player = in.readInt();
                    if (player < 0 || player > 1) throw new IOException("Invalid player slot");
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
                } catch (IOException e) { fail(); }
                catch (IllegalArgumentException e) { fail(); }
            }
        }, "pvp-state");
        reader.setDaemon(true); reader.start();
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
    public void close() {
        closed = true;
        try { socket.close(); } catch (IOException ignored) { }
    }
}
