package pvp;

/** User-supplied endpoint and room code; no personal server address is bundled. */
public final class PvpConnectionSettings {
    public final String host, roomCode;
    public final int port;

    private PvpConnectionSettings(String host, int port, String roomCode) {
        this.host = host; this.port = port; this.roomCode = roomCode;
    }

    public static PvpConnectionSettings parse(String address, String roomCode) {
        String value = address.trim();
        String code = roomCode.trim();
        if (!code.matches("[0-9]{3}"))
            throw new IllegalArgumentException("Connection number must be exactly 3 digits.");
        if (value.isEmpty() || value.contains("://") || value.matches(".*[\\s/\\\\?#@].*"))
            throw new IllegalArgumentException("Enter a server name or IP, optionally followed by :port.");
        String host = value;
        int port = PvpProtocol.DEFAULT_PORT;
        String portText = null;
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end < 2) throw new IllegalArgumentException("Invalid IPv6 address.");
            host = value.substring(1, end);
            if (!host.matches("[0-9a-fA-F:.]+") || host.indexOf(':') < 0)
                throw new IllegalArgumentException("Invalid IPv6 address.");
            if (end + 1 < value.length()) {
                if (value.charAt(end + 1) != ':')
                    throw new IllegalArgumentException("Use [IPv6]:port.");
                portText = value.substring(end + 2);
            }
        } else {
            int colon = value.indexOf(':');
            if (colon >= 0) {
                host = value.substring(0, colon);
                portText = value.substring(colon + 1);
            }
            if (!host.matches("[a-zA-Z0-9](?:[a-zA-Z0-9.-]*[a-zA-Z0-9])?"))
                throw new IllegalArgumentException("Invalid server name. Use [IPv6]:port for IPv6.");
            // A short DuckDNS name is convenient without bundling any specific domain.
            if (!host.contains(".") && !host.equalsIgnoreCase("localhost")) host += ".duckdns.org";
        }
        if (portText != null) {
            try {
                if (!portText.matches("[0-9]{1,5}")) throw new NumberFormatException();
                port = Integer.parseInt(portText);
                if (port < 1 || port > 65535) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Port must be between 1 and 65535.");
            }
        }
        return new PvpConnectionSettings(host, port, code);
    }
}
