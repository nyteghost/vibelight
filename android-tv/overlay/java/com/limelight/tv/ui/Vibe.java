package com.limelight.tv.ui;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.tv.logic.SettingsCatalog;

/**
 * Vibelight design system for Android Views: official colours, a 24 sp minimum
 * type scale for TV, 48 dp minimum targets, rounded panels, one focus indicator
 * (a lavender ring, plus a small scale unless effects are reduced) and shared
 * loading / empty / error states.
 */
public final class Vibe {
    private Vibe() {}

    // Official palette
    public static final int BG = 0xFF060918, BG2 = 0xFF0D1424, SURFACE = 0xFF182137;
    public static final int ACCENT = 0xFF818CF8, ACCENT2 = 0xFFA5B4FC;
    public static final int TEXT = 0xFFE2E8F0, TEXT2 = 0xFF94A3B8;
    // Supporting tones, used sparingly
    public static final int OUTLINE = 0xFF26334D, FOCUS_FILL = 0xFF222C49, ERROR = 0xFFF87171, SCRIM = 0xCC060918;

    // TV type scale (sp). Nothing below 24 sp.
    public static final int DISPLAY = 48, H1 = 36, H2 = 30, BODY = 24, LABEL = 24;
    public static final int MIN_TARGET_DP = 48, RADIUS_DP = 20, RADIUS_SMALL_DP = 14, FOCUS_STROKE_DP = 4;

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    /** True when the user chose Reduce effects or Android's animations are off. */
    public static boolean reducedEffects(Context c) {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(c);
        if (p.getBoolean(SettingsCatalog.KEY_REDUCE_EFFECTS, false)) return true;
        try {
            return Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void applyWindow(Activity a) {
        a.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        a.getWindow().getDecorView().setBackgroundColor(BG);
    }

    public static GradientDrawable rounded(Context c, int fill, int stroke, float radiusDp, float strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) d.setStroke(dp(c, strokeDp), stroke);
        return d;
    }

    /** Background whose only focus cue is a lavender ring. */
    public static Drawable focusable(Context c, int fill, float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, rounded(c, fill == SURFACE ? FOCUS_FILL : fill, ACCENT2, radiusDp, FOCUS_STROKE_DP));
        s.addState(new int[]{android.R.attr.state_pressed}, rounded(c, FOCUS_FILL, ACCENT2, radiusDp, FOCUS_STROKE_DP));
        s.addState(new int[]{}, rounded(c, fill, 0, radiusDp, 0));
        return s;
    }

    /** Gentle focus scale, skipped entirely when effects are reduced. */
    public static void focusMotion(final View v, final float scale) {
        final boolean reduced = reducedEffects(v.getContext());
        final View.OnFocusChangeListener previous = v.getOnFocusChangeListener();
        v.setOnFocusChangeListener((view, has) -> {
            if (!reduced) view.animate().scaleX(has ? scale : 1f).scaleY(has ? scale : 1f).setDuration(120).start();
            if (has) view.post(() -> view.requestRectangleOnScreen(new android.graphics.Rect(0, 0, view.getWidth(), view.getHeight()), false));
            if (previous != null) previous.onFocusChange(view, has);
        });
    }

    public static TextView text(Context c, CharSequence s, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(sp, BODY));
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        t.setLineSpacing(dp(c, 4), 1f);
        return t;
    }

    public static TextView oneLine(Context c, CharSequence s, int sp, int color, boolean bold) {
        TextView t = text(c, s, sp, color, bold);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /** Primary or secondary button: focusable, at least 56 dp tall, one focus ring. */
    public static TextView button(Context c, CharSequence label, boolean primary, View.OnClickListener click) {
        TextView b = oneLine(c, label, LABEL, primary ? BG : TEXT, true);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 56));
        b.setMinWidth(dp(c, 120));
        b.setPadding(dp(c, 24), dp(c, 10), dp(c, 24), dp(c, 10));
        if (primary) {
            StateListDrawable s = new StateListDrawable();
            s.addState(new int[]{android.R.attr.state_focused}, rounded(c, ACCENT2, TEXT, 999, FOCUS_STROKE_DP));
            s.addState(new int[]{}, rounded(c, ACCENT, 0, 999, 0));
            b.setBackground(s);
        } else {
            b.setBackground(focusable(c, SURFACE, 999));
        }
        b.setFocusable(true);
        b.setClickable(true);
        b.setOnClickListener(click);
        focusMotion(b, 1.04f);
        return b;
    }

    public static TextView chip(Context c, CharSequence label, int dotColor) {
        TextView t = oneLine(c, "●  " + label, LABEL, TEXT, false);
        android.text.SpannableString sp = new android.text.SpannableString(t.getText());
        sp.setSpan(new android.text.style.ForegroundColorSpan(dotColor), 0, 1, 0);
        t.setText(sp);
        t.setPadding(dp(c, 16), dp(c, 6), dp(c, 18), dp(c, 6));
        t.setBackground(rounded(c, SURFACE, OUTLINE, 999, 1));
        return t;
    }

    public static LinearLayout row(Context c, int spacingDp) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        if (spacingDp > 0) l.setDividerDrawable(spacer(c, spacingDp));
        l.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        return l;
    }

    public static LinearLayout column(Context c, int spacingDp) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        if (spacingDp > 0) l.setDividerDrawable(spacer(c, spacingDp));
        l.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        return l;
    }

    private static Drawable spacer(Context c, int dp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.TRANSPARENT);
        d.setSize(dp(c, dp), dp(c, dp));
        return d;
    }

    public static ViewGroup.LayoutParams wrap() { return new ViewGroup.LayoutParams(-2, -2); }

    public static LinearLayout.LayoutParams weight(float w) { return new LinearLayout.LayoutParams(0, -2, w); }

    /** Logo + wordmark header used on every screen. */
    public static LinearLayout brand(Context c) {
        LinearLayout r = row(c, 14);
        View logo = new View(c);
        logo.setBackground(new VibeLogoDrawable());
        r.addView(logo, new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)));
        r.addView(oneLine(c, "Vibelight", H2, TEXT, true));
        return r;
    }

    /** Loading, empty and error states share one layout. */
    public static LinearLayout state(Context c, String title, String detail, boolean error) {
        LinearLayout l = column(c, 12);
        l.setGravity(Gravity.CENTER);
        l.setPadding(dp(c, 40), dp(c, 32), dp(c, 40), dp(c, 32));
        l.setBackground(rounded(c, BG2, error ? ERROR : OUTLINE, RADIUS_DP, error ? 2 : 1));
        TextView t = text(c, title, H2, error ? ERROR : TEXT, true);
        t.setGravity(Gravity.CENTER);
        l.addView(t);
        if (detail != null && !detail.isEmpty()) {
            TextView d = text(c, detail, BODY, TEXT2, false);
            d.setGravity(Gravity.CENTER);
            l.addView(d);
        }
        return l;
    }

    public static FrameLayout.LayoutParams frame(int w, int h, int gravity) {
        return new FrameLayout.LayoutParams(w, h, gravity);
    }

    /** A slow pulsing alpha used for skeleton placeholders; static when reduced. */
    public static void pulse(View v) {
        if (reducedEffects(v.getContext())) return;
        ValueAnimator a = ValueAnimator.ofFloat(0.55f, 1f);
        a.setDuration(900);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.addUpdateListener(an -> v.setAlpha((float) an.getAnimatedValue()));
        v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { a.start(); }
            @Override public void onViewDetachedFromWindow(View view) { a.cancel(); }
        });
    }
}
