package com.limelight.tv;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.limelight.Game;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Debug-only checks of the connection screen and in-stream panel without a PC:
 * (A) the panel on a stand-in activity: opens only after "connected", is at most
 *     one third of the width, blocks controller packets while open, releases on
 *     Back, and its bitrate profiles change only the bitrate keys;
 * (B) the real Game activity against an unreachable address: the connection
 *     screen appears and the core's failure is shown with Try again / Back to PCs.
 * No host, pairing, decoding or streaming is simulated or measured.
 */
public final class TvStreamProbe extends Instrumentation {
    private File dir;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    private void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    private void shot(String name) {
        try {
            Bitmap b = getUiAutomation().takeScreenshot();
            if (b == null) return;
            try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) { b.compress(Bitmap.CompressFormat.PNG, 100, out); }
        } catch (Exception ignored) {
            // Screenshots are evidence only.
        }
    }

    private View findText(View v, String text) {
        if (v instanceof TextView && ((TextView) v).getText().toString().contains(text) && v.isShown()) return v;
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
            View f = findText(((ViewGroup) v).getChildAt(i), text);
            if (f != null) return f;
        }
        return null;
    }

    private View waitText(Activity a, String text, long ms) {
        long end = SystemClock.uptimeMillis() + ms;
        while (SystemClock.uptimeMillis() < end) {
            final View[] found = {null};
            runOnMainSync(() -> found[0] = findText(a.getWindow().getDecorView(), text));
            if (found[0] != null) return found[0];
            SystemClock.sleep(200);
        }
        return null;
    }

    private Intent streamIntent(Class<?> cls) {
        return new Intent(getTargetContext(), cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Game.EXTRA_PC_NAME, "Probe PC").putExtra(Game.EXTRA_APP_NAME, "Desktop")
                .putExtra(Game.EXTRA_PC_UUID, "stream-probe-host").putExtra(Game.EXTRA_APP_ID, 1)
                .putExtra(Game.EXTRA_HOST, "127.0.0.1").putExtra(Game.EXTRA_PORT, 9).putExtra(Game.EXTRA_HTTPS_PORT, 9)
                .putExtra(Game.EXTRA_UNIQUEID, "0123456789ABCDEF");
    }

    private void panel(StringBuilder report) {
        final Activity a = startActivitySync(streamIntent(TvPanelHostActivity.class));
        waitForIdleSync();
        check(waitText(a, "Connecting to Probe PC", 3000) != null, "connection screen not shown");
        check(!TvStreamUi.requestPanel(a), "panel must not open before the stream is connected");
        runOnMainSync(() -> TvStreamUi.stageStarting(a, "Desktop"));
        waitForIdleSync();
        shot("S1-connecting");
        TvStreamUi.connectionStarted(a);
        waitForIdleSync(); SystemClock.sleep(500);
        check(waitText(a, "Connecting to Probe PC", 300) == null, "connection screen not removed after connect");

        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(getTargetContext());
        p.edit().putString("list_resolution", "1920x1080").putString("list_fps", "60").putBoolean("checkbox_enable_hdr", true)
                .putString("video_format", "forceh265").putInt("seekbar_bitrate_kbps", 50000).commit();

        check(TvStreamUi.requestPanel(a), "panel did not open after connect");
        waitForIdleSync(); SystemClock.sleep(400);
        final View[] panel = {null};
        runOnMainSync(() -> panel[0] = TvStreamUi.panelView(a));
        check(panel[0] != null, "panel view missing");
        final int[] w = new int[2];
        runOnMainSync(() -> { w[0] = ((View) panel[0].getParent()).getWidth(); w[1] = a.getWindow().getDecorView().getWidth(); });
        check(w[0] > 0 && w[0] * 3 <= w[1] + 3, "panel is " + w[0] + " px of " + w[1] + " px (more than a third)");
        check(TvStreamUi.inputBlocked(), "controller packets not blocked while panel is open");
        final View[] focus = {null};
        runOnMainSync(() -> focus[0] = a.getCurrentFocus());
        check(focus[0] instanceof TextView && "Resume".contentEquals(((TextView) focus[0]).getText()), "panel did not focus Resume");
        shot("S4-panel");

        View best = waitText(a, "Best picture", 1000);
        check(best != null, "Best picture profile missing");
        runOnMainSync(best::performClick);
        waitForIdleSync();
        check(p.getInt("seekbar_bitrate_kbps", 0) == 75000, "bitrate not applied");
        check("1920x1080".equals(p.getString("list_resolution", "")) && "60".equals(p.getString("list_fps", ""))
                && p.getBoolean("checkbox_enable_hdr", false) && "forceh265".equals(p.getString("video_format", "")),
                "profile changed a non-bitrate preference");
        p.edit().putBoolean("checkbox_enable_hdr", false).putString("video_format", "auto").commit();

        runOnMainSync(() -> {
            TvStreamUi.onKey(a, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
            TvStreamUi.onKey(a, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
        });
        waitForIdleSync();
        check(!TvStreamUi.inputBlocked(), "input still blocked after closing the panel");
        final View[] after = {null};
        runOnMainSync(() -> after[0] = TvStreamUi.panelView(a));
        check(after[0] == null, "panel still open after Back");
        runOnMainSync(a::finish);
        waitForIdleSync();
        report.append("panel ").append(w[0]).append("/").append(w[1]).append(" px, input gate and bitrate-only profile verified; ");
    }

    private void gameFailure(StringBuilder report) {
        ActivityMonitor m = addMonitor(Game.class.getName(), null, false);
        getTargetContext().startActivity(streamIntent(Game.class));
        Activity game = waitForMonitorWithTimeout(m, 15000);
        removeMonitor(m);
        check(game != null, "Game activity did not start");
        View connecting = waitText(game, "Connecting to Probe PC", 5000);
        View failure = waitText(game, "Try again", 30000);
        if (connecting != null) report.append("Game connection screen shown; ");
        check(failure != null, "Game failure screen not shown for an unreachable PC");
        check(waitText(game, "Back to PCs", 1000) != null, "Back to PCs missing");
        shot("S1-failure-unreachable");
        runOnMainSync(game::finish);
        waitForIdleSync();
        report.append("Game failure screen with Try again / Back to PCs verified");
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            dir = new File(getTargetContext().getFilesDir(), "probe");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            StringBuilder report = new StringBuilder();
            panel(report);
            gameFailure(report);
            result.putString("stream", "Stream UI checks passed: " + report + ". No PC or streaming was simulated.");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
