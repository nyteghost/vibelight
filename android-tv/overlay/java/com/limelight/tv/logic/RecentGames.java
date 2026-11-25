package com.limelight.tv.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Truthful recent-games history. An entry is recorded only when a stream actually
 * started (the core's connectionStarted callback), never inferred from per-host
 * launch timestamps. Stored as a compact escaped text record list, newest first.
 */
public final class RecentGames {
    public static final int MAX = 24;

    public static final class Entry {
        public final String hostUuid, appName;
        public final int appId;
        public final long startedAt;
        public Entry(String hostUuid, int appId, String appName, long startedAt) {
            this.hostUuid = hostUuid; this.appId = appId; this.appName = appName; this.startedAt = startedAt;
        }
    }

    private RecentGames() {}

    public static List<Entry> decode(String stored) {
        List<Entry> out = new ArrayList<>();
        if (stored == null || stored.isEmpty()) return out;
        for (String line : stored.split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length != 4) continue;
            try {
                String host = unescape(f[0]), name = unescape(f[2]);
                int id = Integer.parseInt(f[1]);
                long at = Long.parseLong(f[3]);
                if (host.isEmpty() || at <= 0) continue;
                out.add(new Entry(host, id, name, at));
            } catch (NumberFormatException ignored) {
                // Skip a corrupt record rather than losing the rest.
            }
        }
        return out;
    }

    public static String encode(List<Entry> entries) {
        StringBuilder b = new StringBuilder();
        for (Entry e : entries) {
            if (b.length() > 0) b.append('\n');
            b.append(escape(e.hostUuid)).append('\t').append(e.appId).append('\t')
             .append(escape(e.appName)).append('\t').append(e.startedAt);
        }
        return b.toString();
    }

    /** Adds or moves an entry to the front; one entry per host and app. */
    public static List<Entry> record(List<Entry> current, String hostUuid, int appId, String appName, long now) {
        List<Entry> out = new ArrayList<>();
        if (hostUuid == null || hostUuid.isEmpty() || now <= 0) return new ArrayList<>(current);
        out.add(new Entry(hostUuid, appId, appName == null ? "" : appName, now));
        for (Entry e : current) {
            if (e.hostUuid.equals(hostUuid) && e.appId == appId) continue;
            if (out.size() >= MAX) break;
            out.add(e);
        }
        return out;
    }

    public static List<Entry> forHost(List<Entry> all, String hostUuid) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) if (e.hostUuid.equals(hostUuid)) out.add(e);
        Collections.sort(out, (a, b) -> Long.compare(b.startedAt, a.startedAt));
        return out;
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    static String unescape(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                b.append(n == 't' ? '\t' : n == 'n' ? '\n' : n);
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }
}
