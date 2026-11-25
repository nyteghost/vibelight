package com.limelight.tv.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** The Vibelight mark: a rounded lavender play triangle with a soft inner highlight. */
public final class VibeLogoDrawable extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path(), innerPath = new Path();

    @Override protected void onBoundsChange(Rect b) {
        float s = Math.min(b.width(), b.height());
        float ox = b.left + (b.width() - s) / 2f, oy = b.top + (b.height() - s) / 2f;
        path.reset();
        path.moveTo(ox + s * 0.18f, oy + s * 0.08f);
        path.lineTo(ox + s * 0.92f, oy + s * 0.50f);
        path.lineTo(ox + s * 0.18f, oy + s * 0.92f);
        path.close();
        innerPath.reset();
        innerPath.moveTo(ox + s * 0.34f, oy + s * 0.34f);
        innerPath.lineTo(ox + s * 0.62f, oy + s * 0.50f);
        innerPath.lineTo(ox + s * 0.34f, oy + s * 0.66f);
        innerPath.close();
        fill.setShader(new LinearGradient(ox, oy, ox + s, oy + s, Vibe.ACCENT, Vibe.ACCENT2, Shader.TileMode.CLAMP));
        fill.setPathEffect(new CornerPathEffect(s * 0.12f));
        inner.setColor(0x660D1424);
        inner.setPathEffect(new CornerPathEffect(s * 0.05f));
    }

    @Override public void draw(Canvas canvas) {
        canvas.drawPath(path, fill);
        canvas.drawPath(innerPath, inner);
    }

    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); inner.setAlpha(alpha * 0x66 / 255); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter cf) { fill.setColorFilter(cf); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
