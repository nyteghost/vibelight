package com.limelight.tv.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Rounded Vibelight dialog with one focus indicator and explicit buttons. */
public final class VibeDialog {
    private VibeDialog() {}

    public static final class Choice {
        final String label; final boolean primary; final Runnable action;
        public Choice(String label, boolean primary, Runnable action) { this.label = label; this.primary = primary; this.action = action; }
    }

    public static Dialog show(Activity a, String title, CharSequence message, Choice... choices) {
        return show(a, title, message, null, choices);
    }

    public static Dialog show(Activity a, String title, CharSequence message, View extra, Choice... choices) {
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = Vibe.column(a, 18);
        box.setPadding(Vibe.dp(a, 36), Vibe.dp(a, 32), Vibe.dp(a, 36), Vibe.dp(a, 32));
        box.setBackground(Vibe.rounded(a, Vibe.BG2, Vibe.OUTLINE, Vibe.RADIUS_DP, 1));
        box.addView(Vibe.text(a, title, Vibe.H2, Vibe.TEXT, true));
        if (message != null && message.length() > 0) box.addView(Vibe.text(a, message, Vibe.BODY, Vibe.TEXT2, false));
        if (extra != null) box.addView(extra);
        LinearLayout row = Vibe.row(a, 16);
        row.setGravity(Gravity.END);
        TextView first = null;
        for (final Choice c : choices) {
            TextView b = Vibe.button(a, c.label, c.primary, v -> { d.dismiss(); if (c.action != null) c.action.run(); });
            row.addView(b);
            if (first == null || c.primary) first = b;
        }
        if (choices.length == 0) {
            first = Vibe.button(a, "OK", true, v -> d.dismiss());
            row.addView(first);
        }
        box.addView(row, new LinearLayout.LayoutParams(-1, -2));
        d.setContentView(box);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setLayout(Math.min(Vibe.dp(a, 720), a.getResources().getDisplayMetrics().widthPixels - Vibe.dp(a, 64)), -2);
            w.setDimAmount(0.7f);
        }
        final TextView focus = first;
        d.setOnShowListener(x -> { if (focus != null) focus.requestFocus(); });
        if (!a.isFinishing()) d.show();
        return d;
    }
}
