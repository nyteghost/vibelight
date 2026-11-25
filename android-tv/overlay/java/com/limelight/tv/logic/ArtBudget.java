package com.limelight.tv.logic;

/** Bounded artwork cache sizing. */
public final class ArtBudget {
    public static final long MAX_MEMORY_BYTES = 48L * 1024 * 1024;
    public static final long MIN_MEMORY_BYTES = 8L * 1024 * 1024;
    public static final long MAX_DISK_BYTES = 96L * 1024 * 1024;
    /** Largest decoded artwork edge; host box art is downsampled to this. */
    public static final int MAX_EDGE_PX = 720;

    private ArtBudget() {}

    /** One eighth of the app's heap class, clamped to [8, 48] MiB. */
    public static long memoryBytes(long maxHeapBytes) {
        long b = maxHeapBytes / 8;
        return Math.max(MIN_MEMORY_BYTES, Math.min(MAX_MEMORY_BYTES, b));
    }

    /** Power-of-two downsample factor so the longer edge is at most MAX_EDGE_PX. */
    public static int sampleSize(int width, int height) {
        int edge = Math.max(width, height), s = 1;
        while (edge / (s * 2) >= MAX_EDGE_PX) s *= 2;
        return s;
    }
}
