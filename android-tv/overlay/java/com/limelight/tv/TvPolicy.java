package com.limelight.tv;

/** Android-independent streaming policy. No timers, networking or implicit setting writes. */
public final class TvPolicy {
    private TvPolicy() {}
    public enum Profile {
        LOW_LATENCY("Low Latency", 35000, "latency"),
        BALANCED("Balanced", 50000, "balanced"),
        BEST_PICTURE("Best Picture", 75000, "balanced");
        public final String label, pacing;
        public final int kbps;
        Profile(String label, int kbps, String pacing) {
            this.label = label; this.kbps = kbps; this.pacing = pacing;
        }
    }
    public static int lowerBitrate(int kbps) {
        if (kbps < 10000 || kbps > 150000) return -1;
        return Math.max(10000, kbps * 4 / 5);
    }
    public static String guidance(int samples, long spanMs, double lossPercent,
                                  double decodeMs, int fps) {
        if (samples < 6 || spanMs < 15000 || fps <= 0 ||
            !Double.isFinite(lossPercent) || lossPercent < 0 || lossPercent > 100 ||
            !Double.isFinite(decodeMs) || decodeMs < 0) {
            return "More continuous measured playback is needed. No change recommended.";
        }
        if (lossPercent >= 2) return "Sustained network frame loss: try 20% less bitrate for the next stream. This does not measure packet loss.";
        if (decodeMs > 1000.0 / fps) return "Decoder time exceeds one frame interval. Try 1080p60 or H.264 in Advanced settings.";
        return "No sustained frame-loss or decoder-time issue detected in the available samples. This is not a controller-to-screen latency measurement.";
    }
    public static boolean canReduce(int samples, long spanMs, double loss, double decode, int fps) {
        return samples >= 6 && spanMs >= 15000 && Double.isFinite(loss) && loss >= 2 && loss <= 100 &&
                Double.isFinite(decode) && decode >= 0 && fps > 0;
    }
}
