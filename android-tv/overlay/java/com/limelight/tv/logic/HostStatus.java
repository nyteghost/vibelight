package com.limelight.tv.logic;

/**
 * Pure host-card status derived only from what the host service actually reports.
 * "Asleep" is never inferred: an offline PC is only described as wakeable when a
 * MAC address is known, because Wake-on-LAN needs it.
 */
public final class HostStatus {
    public enum Reachability { ONLINE, OFFLINE, UNKNOWN }

    public enum Kind { CHECKING, OFFLINE, OFFLINE_WAKEABLE, PAIRING_NEEDED, READY, GAME_RUNNING }

    public final Kind kind;
    public final String label;
    public final String detail;
    public final boolean canOpenLibrary, canPair, canWake, canResume;

    private HostStatus(Kind kind, String label, String detail, boolean library, boolean pair, boolean wake, boolean resume) {
        this.kind = kind; this.label = label; this.detail = detail;
        this.canOpenLibrary = library; this.canPair = pair; this.canWake = wake; this.canResume = resume;
    }

    /**
     * @param paired      host reports this client as paired and a server certificate is pinned
     * @param runningGame host-reported running app id (0 = none)
     * @param hasMac      a MAC address is known for Wake-on-LAN
     */
    public static HostStatus of(Reachability reach, boolean paired, int runningGame, boolean hasMac) {
        if (reach == null || reach == Reachability.UNKNOWN) {
            return new HostStatus(Kind.CHECKING, "Checking", "Waiting for this PC to answer", false, false, hasMac, false);
        }
        if (reach == Reachability.OFFLINE) {
            if (hasMac) return new HostStatus(Kind.OFFLINE_WAKEABLE, "Offline", "Not answering · Wake-on-LAN available", false, false, true, false);
            return new HostStatus(Kind.OFFLINE, "Offline", "Not answering on the network", false, false, false, false);
        }
        if (!paired) {
            return new HostStatus(Kind.PAIRING_NEEDED, "Pairing needed", "Pair once with a four-digit PIN", false, true, false, false);
        }
        if (runningGame != 0) {
            return new HostStatus(Kind.GAME_RUNNING, "Game running", "A session is active on this PC", true, false, false, true);
        }
        return new HostStatus(Kind.READY, "Online", "Ready to play", true, false, false, false);
    }
}
