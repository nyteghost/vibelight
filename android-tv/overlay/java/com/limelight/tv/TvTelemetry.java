package com.limelight.tv;

import android.content.Context;
import android.os.SystemClock;

/** Session-local summary sourced from the decoder's real measurement windows. No disk or upload. */
public final class TvTelemetry {
    private TvTelemetry() {}
    private static int count, targetFps;
    private static long start, last;
    private static double lossSum, decodeSum;
    public static synchronized void reset() { count = 0; start = last = 0; lossSum = decodeSum = 0; }
    public static synchronized void sample(Context context, int fps, double loss, double decode) {
        if (!TvPreferences.prefs(context).getBoolean("vibe_smart_local", true)) { reset(); return; }
        long now = SystemClock.elapsedRealtime();
        if (!Double.isFinite(loss) || loss < 0 || loss > 100 || !Double.isFinite(decode) || decode < 0 || fps <= 0) { reset(); return; }
        if (count > 0 && (now <= last || now - last > 4000 || fps != targetFps)) reset();
        if (count == 0) { start = now; targetFps = fps; }
        last = now; count++; lossSum += loss; decodeSum += decode;
    }
    public static synchronized String guidance() {
        if (last == 0 || SystemClock.elapsedRealtime() - last > 300000) return "No recent measured session. Enable the performance overlay and play for at least 15 seconds.";
        return TvPolicy.guidance(count, last-start, lossSum/count, decodeSum/count, targetFps);
    }
    public static synchronized boolean canReduce() {
        return last > 0 && SystemClock.elapsedRealtime() - last <= 300000 &&
            TvPolicy.canReduce(count, last-start, lossSum/count, decodeSum/count, targetFps);
    }
}
