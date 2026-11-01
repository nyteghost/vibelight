package com.limelight.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public final class TvPreferences {
    private TvPreferences() {}
    public static SharedPreferences prefs(Context c) { return PreferenceManager.getDefaultSharedPreferences(c); }
    public static void initialize(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean("vibe_tv_initialized", false)) {
            profile(c, TvPolicy.Profile.BALANCED);
            p.edit().putBoolean("vibe_tv_initialized", true).putBoolean("checkbox_small_icon_mode", false).apply();
        }
    }
    public static void profile(Context c, TvPolicy.Profile profile) {
        prefs(c).edit().putString("list_resolution", "3840x2160").putString("list_fps", "60")
            .putInt("seekbar_bitrate_kbps", profile.kbps).putString("video_format", "auto")
            .putString("frame_pacing", profile.pacing).putBoolean("checkbox_enable_hdr", false)
            .putString("vibe_tv_profile", profile.label).remove("vibe_undo_bitrate")
            .remove("vibe_applied_bitrate").apply();
    }
    /** Explicit next-stream adjustment; compare current values on undo to preserve manual edits. */
    public static boolean reduce(Context c) {
        SharedPreferences p = prefs(c);
        int previous = p.getInt("seekbar_bitrate_kbps", 50000), next = TvPolicy.lowerBitrate(previous);
        if (next < 0 || next == previous || p.contains("vibe_undo_bitrate")) return false;
        return p.edit().putInt("vibe_undo_bitrate", previous).putInt("vibe_applied_bitrate", next)
            .putInt("seekbar_bitrate_kbps", next).commit();
    }
    public static boolean undo(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.contains("vibe_undo_bitrate") || p.getInt("seekbar_bitrate_kbps", 0) != p.getInt("vibe_applied_bitrate", -1)) return false;
        return p.edit().putInt("seekbar_bitrate_kbps", p.getInt("vibe_undo_bitrate", 50000))
            .remove("vibe_undo_bitrate").remove("vibe_applied_bitrate").commit();
    }
}
