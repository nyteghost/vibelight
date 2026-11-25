package com.limelight.tv.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Calm connection ring. It rotates slowly while connecting; with reduced effects
 * it is a static three-quarter ring. It never shows a percentage.
 */
public final class RingView extends View {
    public enum Mode { WORKING, DONE, FAILED }

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final boolean reduced;
    private ValueAnimator spin;
    private float angle;
    private Mode mode = Mode.WORKING;

    public RingView(Context c) {
        super(c);
        reduced = Vibe.reducedEffects(c);
        float w = Vibe.dp(c, 8);
        track.setStyle(Paint.Style.STROKE); track.setStrokeWidth(w); track.setColor(Vibe.SURFACE);
        arc.setStyle(Paint.Style.STROKE); arc.setStrokeWidth(w); arc.setStrokeCap(Paint.Cap.ROUND); arc.setColor(Vibe.ACCENT2);
        setFocusable(false);
    }

    public void setMode(Mode m) {
        mode = m;
        arc.setColor(m == Mode.FAILED ? Vibe.ERROR : Vibe.ACCENT2);
        if (m != Mode.WORKING) stop(); else start();
        invalidate();
    }

    private void start() {
        if (reduced || spin != null || !isAttachedToWindow()) return;
        spin = ValueAnimator.ofFloat(0f, 360f);
        spin.setDuration(2400);
        spin.setInterpolator(new LinearInterpolator());
        spin.setRepeatCount(ValueAnimator.INFINITE);
        spin.addUpdateListener(a -> { angle = (float) a.getAnimatedValue(); invalidate(); });
        spin.start();
    }

    private void stop() {
        if (spin != null) { spin.cancel(); spin = null; }
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); if (mode == Mode.WORKING) start(); }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas canvas) {
        float inset = track.getStrokeWidth();
        box.set(inset, inset, getWidth() - inset, getHeight() - inset);
        canvas.drawOval(box, track);
        if (mode == Mode.DONE || mode == Mode.FAILED) canvas.drawArc(box, -90, 360, false, arc);
        else canvas.drawArc(box, angle - 90, reduced ? 270 : 110, false, arc);
    }
}
