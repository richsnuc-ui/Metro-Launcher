package com.metro.launcher;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

/** Turns app icons into white, single-color glyphs for the monochrome look. */
final class Icons {
    private Icons() {}

    /** Returns a new drawable (safe to use in one place only). */
    static Drawable mono(Drawable d, Resources r) {
        if (d == null) return null;
        if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) d;
            Drawable layer = null;
            if (Build.VERSION.SDK_INT >= 33) layer = a.getMonochrome(); // Android 13 themed icon
            if (layer == null) layer = a.getForeground();
            if (layer != null) {
                Drawable m = copy(layer, r);
                m.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
                // Adaptive layers are 108dp with the glyph in the middle 72dp: draw them larger.
                return new Expand(m);
            }
        }
        Drawable m = copy(d, r);
        m.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
        return m;
    }

    static Drawable copy(Drawable d, Resources r) {
        Drawable.ConstantState cs = d.getConstantState();
        return cs != null ? cs.newDrawable(r).mutate() : d.mutate();
    }

    /** Draws its child 1.5x larger than its bounds, centered. */
    static final class Expand extends Drawable {
        private final Drawable inner;

        Expand(Drawable inner) {
            this.inner = inner;
        }

        @Override
        protected void onBoundsChange(Rect b) {
            int dx = b.width() / 4, dy = b.height() / 4;
            inner.setBounds(b.left - dx, b.top - dy, b.right + dx, b.bottom + dy);
        }

        @Override
        public void draw(Canvas canvas) {
            inner.draw(canvas);
        }

        @Override
        public void setAlpha(int alpha) {
            inner.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            inner.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return inner.getIntrinsicWidth() * 2 / 3;
        }

        @Override
        public int getIntrinsicHeight() {
            return inner.getIntrinsicHeight() * 2 / 3;
        }
    }
}
