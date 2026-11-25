package com.limelight.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

/** Debug-only stand-in for the stream activity so the panel can be tested without a PC. */
public final class TvPanelHostActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        View surface = new View(this);
        surface.setBackgroundColor(0xFF1E293B);
        surface.setFocusable(true);
        setContentView(surface);
        TvStreamUi.attach(this);
    }

    @Override protected void onDestroy() {
        TvStreamUi.detach(this);
        super.onDestroy();
    }
}
