package pvp;

/** Shared default for normal game launches, scripts and IDE runs. */
public final class PvpServerAddress {
    public static final String DEFAULT_HOST = "riftflow.duckdns.org";
    private static final String LAN_HOST = "172.30.1.48";
    private PvpServerAddress() { }

    public static String host() { return System.getProperty("pvp.host", DEFAULT_HOST).trim(); }

    /** Fall back to LAN when the router cannot loop its public address back inside. */
    public static String[] candidates() {
        String host = host();
        if (System.getProperty("pvp.host") == null)
            return new String[] { host, LAN_HOST };
        return new String[] { host };
    }

}
