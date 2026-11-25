package com.limelight.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.limelight.tv.logic.BitrateProfile;
import com.limelight.tv.logic.RecentGames;

import java.util.List;

/** Vibelight preference helpers. Bitrate changes are bitrate-only by design. */
public final class TvPrefs {
    private TvPrefs() {}

    public static final String KEY_RECENT_GAMES = "vibe_recent_games";
    public static final String KEY_LAST_HOST = "vibe_last_host";

    public static SharedPreferences get(Context c) { return PreferenceManager.getDefaultSharedPreferences(c); }

    /**
     * Changes the core bitrate and the profile label only. Resolution, FPS, HDR,
     * codec, frame pacing and every other preference keep their current values.
     */
    public static boolean applyBitrateOnly(Context c, BitrateProfile p) {
        return get(c).edit()
                .putInt(BitrateProfile.KEY_BITRATE, p.kbps)
                .putString(BitrateProfile.KEY_LABEL, p.label)
                .remove("vibe_undo_bitrate").remove("vibe_applied_bitrate")
                .commit();
    }

    public static List<RecentGames.Entry> recent(Context c) {
        return RecentGames.decode(get(c).getString(KEY_RECENT_GAMES, ""));
    }

    public static String lastHost(Context c) { return get(c).getString(KEY_LAST_HOST, null); }

    public static void setLastHost(Context c, String uuid) {
        if (uuid != null) get(c).edit().putString(KEY_LAST_HOST, uuid).apply();
    }
}
