package com.limelight.tv;

import java.util.concurrent.atomic.AtomicInteger;
/** Tracks live stream activities so the updater cannot interrupt playback. */
public final class TvSessionState {
    private static final AtomicInteger count=new AtomicInteger();
    private TvSessionState(){}
    public static void enter(){count.incrementAndGet();}
    public static void leave(){count.updateAndGet(n->Math.max(0,n-1));}
    public static boolean active(){return count.get()>0;}
}
