package com.limelight.tv.logic;

/**
 * Bitrate-only streaming profiles. Selecting one changes exactly two preference
 * keys (the core bitrate and the profile label); resolution, FPS, HDR, codec,
 * frame pacing and every other preference are left untouched. The Android core
 * has no runtime bitrate API, so a selection applies to the next stream.
 */
public enum BitrateProfile {
    LOW_LATENCY("Low latency", 35000, "Smaller frames, fastest response"),
    BALANCED("Balanced", 50000, "Recommended for most wired networks"),
    BEST_PICTURE("Best picture", 75000, "Highest detail; needs a strong network");

    public static final String KEY_BITRATE = "seekbar_bitrate_kbps";
    public static final String KEY_LABEL = "vibe_tv_profile";

    public final String label, description;
    public final int kbps;

    BitrateProfile(String label, int kbps, String description) {
        this.label = label; this.kbps = kbps; this.description = description;
    }

    /** The profile whose bitrate exactly matches, or null for a custom bitrate. */
    public static BitrateProfile match(int kbps) {
        for (BitrateProfile p : values()) if (p.kbps == kbps) return p;
        return null;
    }

    public static String describe(int kbps) {
        BitrateProfile p = match(kbps);
        return (p == null ? "Custom" : p.label) + " · " + (kbps / 1000) + " Mbps";
    }
}
