package com.limelight.tv.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure filtering and sorting for the game library. */
public final class Library {
    public enum Sort { NAME, RECENT }

    public static final class Game {
        public final int id;
        public final String name;
        public final boolean hdr;
        public Game(int id, String name, boolean hdr) { this.id = id; this.name = name == null ? "" : name; this.hdr = hdr; }
    }

    private Library() {}

    public static List<Game> view(List<Game> games, String query, Sort sort, List<RecentGames.Entry> recentForHost) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Game> out = new ArrayList<>();
        for (Game g : games) {
            if (q.isEmpty() || matches(g.name, q)) out.add(g);
        }
        final Map<Integer, Long> last = new HashMap<>();
        if (recentForHost != null) for (RecentGames.Entry e : recentForHost) {
            Long cur = last.get(e.appId);
            if (cur == null || e.startedAt > cur) last.put(e.appId, e.startedAt);
        }
        Comparator<Game> byName = (a, b) -> {
            int c = a.name.compareToIgnoreCase(b.name);
            return c != 0 ? c : Integer.compare(a.id, b.id);
        };
        if (sort == Sort.RECENT) {
            Collections.sort(out, (a, b) -> {
                long la = last.containsKey(a.id) ? last.get(a.id) : 0, lb = last.containsKey(b.id) ? last.get(b.id) : 0;
                if (la != lb) return Long.compare(lb, la);
                return byName.compare(a, b);
            });
        } else {
            Collections.sort(out, byName);
        }
        return out;
    }

    /** Every whitespace-separated query word must appear in the name. */
    static boolean matches(String name, String q) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String word : q.split("\\s+")) if (!word.isEmpty() && !n.contains(word)) return false;
        return true;
    }
}
