package com.limelight.tv;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Debug-only design checks on real rendered Views: every visible text is at least
 * 24 sp, every visible focusable control is at least 48 dp tall, and every screen
 * offers a focusable control (no dead ends). No host, pairing or streaming is simulated.
 */
public final class TvDesignProbe extends Instrumentation {
    private final List<String> failures = new ArrayList<>();
    private final StringBuilder report = new StringBuilder();

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }

    private void walk(String screen, View v, DisplayMetrics m, int[] counts) {
        if (v.getVisibility() != View.VISIBLE) return;
        if (v instanceof TextView && ((TextView) v).getText().length() > 0) {
            float sp = ((TextView) v).getTextSize() / m.scaledDensity;
            counts[0]++;
            if (sp < 23.5f) failures.add(screen + ": text '" + ((TextView) v).getText() + "' is " + sp + " sp");
        }
        if (v.isFocusable() && v.getHeight() > 0) {
            counts[1]++;
            float dp = v.getHeight() / m.density;
            if (dp < 47.5f) failures.add(screen + ": focusable " + v.getClass().getSimpleName() + " is " + dp + " dp tall");
        }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) walk(screen, ((ViewGroup) v).getChildAt(i), m, counts);
    }

    private void check(String screen, Class<? extends Activity> cls, Bundle extras) {
        Intent i = new Intent(getTargetContext(), cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (extras != null) i.putExtras(extras);
        final Activity a = startActivitySync(i);
        waitForIdleSync();
        SystemClock.sleep(1500);
        final int[] counts = new int[2];
        runOnMainSync(() -> walk(screen, a.getWindow().getDecorView(), a.getResources().getDisplayMetrics(), counts));
        if (counts[1] == 0) failures.add(screen + ": no focusable control");
        report.append(screen).append(": ").append(counts[0]).append(" texts, ").append(counts[1]).append(" focusable controls; ");
        runOnMainSync(a::finish);
        waitForIdleSync();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            PreferenceManager.getDefaultSharedPreferences(getTargetContext()).edit().putBoolean("vibe_reduce_effects", false).commit();
            check("PC picker", TvLauncherActivity.class, null);
            check("Settings", TvSettingsActivity.class, null);
            check("Add PC", TvAddPcActivity.class, null);
            Bundle unknown = new Bundle();
            unknown.putString(TvPairActivity.EXTRA_HOST, "design-probe-unknown-host");
            check("Pairing (unknown host)", TvPairActivity.class, unknown);
            Bundle lib = new Bundle();
            lib.putString(TvLibraryActivity.EXTRA_HOST, "design-probe-unknown-host");
            check("Library (unknown host)", TvLibraryActivity.class, lib);
            PreferenceManager.getDefaultSharedPreferences(getTargetContext()).edit().putBoolean("vibe_reduce_effects", true).commit();
            check("PC picker (reduced effects)", TvLauncherActivity.class, null);
            PreferenceManager.getDefaultSharedPreferences(getTargetContext()).edit().putBoolean("vibe_reduce_effects", false).commit();
            if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
            result.putString("stream", "Design checks passed: " + report + "Text >= 24 sp, focusable controls >= 48 dp.");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
