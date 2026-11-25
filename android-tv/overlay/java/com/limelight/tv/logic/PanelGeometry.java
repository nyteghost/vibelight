package com.limelight.tv.logic;

/** In-stream panel sizing: never wider than one third of the gameplay area. */
public final class PanelGeometry {
    private PanelGeometry() {}

    /**
     * @param screenWidthPx full width of the stream window
     * @param preferredPx   design width (e.g. 560dp in px)
     * @param minimumPx     smallest usable width (e.g. 320dp in px)
     */
    public static int width(int screenWidthPx, int preferredPx, int minimumPx) {
        if (screenWidthPx <= 0) return 0;
        int third = screenWidthPx / 3;
        int w = Math.min(preferredPx, third);
        if (w < minimumPx) w = Math.min(minimumPx, third);
        return Math.max(0, w);
    }
}
