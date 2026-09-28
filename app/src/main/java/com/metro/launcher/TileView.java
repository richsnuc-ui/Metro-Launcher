package com.metro.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

/**
 * A single Start tile, drawn directly on a canvas (no nested layouts, so scrolling stays fast).
 * App tiles show an icon and name; live tiles flip between two faces.
 */
final class TileView extends View implements Tile {
    final TileSpec spec;
    private Drawable icon;
    private Bitmap art;
    private final Rect artSrc = new Rect(), artDst = new Rect();
    private final Paint bg = new Paint();
    private final Paint artPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint shade = new Paint();
    private final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint big = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint line = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private String fBig, f1, f2, bBig, b1, b2;
    private boolean hasBack, showingBack, flipping;

    TileView(Context c, TileSpec s) {
        super(c);
        spec = s;
        Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
        label.setColor(Color.WHITE);
        label.setTypeface(regular);
        label.setTextSize(Util.sp(c, 12));
        big.setColor(Color.WHITE);
        big.setTypeface(light);
        line.setColor(Color.WHITE);
        line.setTypeface(regular);
        shade.setColor(0x73000000);
        setClickable(true);
        setLongClickable(true);
        setContentDescription(s.label);
        setCameraDistance(8000 * getResources().getDisplayMetrics().density);
    }

    @Override
    public TileSpec spec() {
        return spec;
    }

    /** d must be a drawable owned by this tile (not shared with another view). */
    void setIcon(Drawable d) {
        icon = d;
        invalidate();
    }

    void setArt(Bitmap b) {
        if (art == b) return;
        art = b;
        invalidate();
    }

    void setStyle(int color, int alpha, int shape) {
        bg.setColor((color & 0x00FFFFFF) | (alpha << 24));
        Tile.applyShape(this, shape);
        invalidate();
    }

    void setFaces(String fBig, String f1, String f2, String bBig, String b1, String b2, boolean hasBack) {
        if (TextUtils.equals(this.fBig, fBig) && TextUtils.equals(this.f1, f1) && TextUtils.equals(this.f2, f2)
                && TextUtils.equals(this.bBig, bBig) && TextUtils.equals(this.b1, b1)
                && TextUtils.equals(this.b2, b2) && this.hasBack == hasBack) {
            return; // nothing changed: skip the redraw
        }
        this.fBig = fBig;
        this.f1 = f1;
        this.f2 = f2;
        this.bBig = bBig;
        this.b1 = b1;
        this.b2 = b2;
        this.hasBack = hasBack;
        if (!hasBack) showingBack = false;
        invalidate();
    }

    boolean canFlip() {
        return hasBack && !flipping && !isPressed() && isAttachedToWindow();
    }

    /** Windows Phone style flip: fold away on the X axis, swap faces, fold back in. */
    void flip() {
        if (!canFlip()) return;
        flipping = true;
        ObjectAnimator out = ObjectAnimator.ofFloat(this, View.ROTATION_X, 0f, 90f);
        out.setDuration(230);
        out.setInterpolator(new AccelerateInterpolator());
        out.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                showingBack = !showingBack;
                invalidate();
                ObjectAnimator in = ObjectAnimator.ofFloat(TileView.this, View.ROTATION_X, -90f, 0f);
                in.setDuration(230);
                in.setInterpolator(new DecelerateInterpolator());
                in.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator a2) {
                        flipping = false;
                    }
                });
                in.start();
            }
        });
        out.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                animate().setStartDelay(0).scaleX(0.95f).scaleY(0.95f).setDuration(90).start();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                animate().setStartDelay(0).scaleX(1f).scaleY(1f).setDuration(140).start();
                break;
            default:
                break;
        }
        return super.onTouchEvent(e);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawRect(0, 0, w, h, bg);
        Context ctx = getContext();
        int pad = Util.dp(ctx, 8);
        boolean small = spec.size == TileSpec.SMALL;
        float avail = w - 2f * pad;

        if (art != null && !art.isRecycled()) {
            // Cover-crop the album art to the tile, then darken it so text stays readable.
            float scale = Math.max((float) w / art.getWidth(), (float) h / art.getHeight());
            int sw = Math.round(w / scale), sh = Math.round(h / scale);
            int sx = (art.getWidth() - sw) / 2, sy = (art.getHeight() - sh) / 2;
            artSrc.set(sx, sy, sx + sw, sy + sh);
            artDst.set(0, 0, w, h);
            c.drawBitmap(art, artSrc, artDst, artPaint);
            c.drawRect(0, 0, w, h, shade);
        }

        if (spec.type == TileSpec.APP) {
            if (icon != null) {
                int s = small ? (int) (Math.min(w, h) * 0.55f) : (int) (Math.min(w, h) * 0.40f);
                int cx = w / 2;
                int cy = small ? h / 2 : h / 2 - Util.dp(ctx, 6);
                icon.setBounds(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
                icon.draw(c);
            }
        } else {
            String bigText = showingBack ? bBig : fBig;
            String l1 = showingBack ? b1 : f1;
            String l2 = showingBack ? b2 : f2;
            float y = pad;
            if (bigText != null && !bigText.isEmpty()) {
                float size = small ? Util.sp(ctx, 24)
                        : spec.size == TileSpec.MEDIUM ? Util.sp(ctx, 46) : Util.sp(ctx, 54);
                big.setTextSize(size);
                y += -big.ascent() * 0.92f;
                c.drawText(fit(bigText, big, avail), pad, y, big);
                y += big.descent() * 0.6f;
            }
            line.setTextSize(small ? Util.sp(ctx, 11) : Util.sp(ctx, 15));
            if (l1 != null && !l1.isEmpty()) {
                line.setAlpha(255);
                y += -line.ascent() + Util.dp(ctx, 2);
                c.drawText(fit(l1, line, avail), pad, y, line);
                y += line.descent();
            }
            if (!small && l2 != null && !l2.isEmpty()) {
                line.setAlpha(210);
                y += -line.ascent();
                c.drawText(fit(l2, line, avail), pad, y, line);
            }
        }

        if (!small && spec.label != null) {
            c.drawText(fit(spec.label, label, avail), pad, h - pad - label.descent(), label);
        }
    }

    private static String fit(String s, TextPaint p, float width) {
        return TextUtils.ellipsize(s, p, Math.max(0, width), TextUtils.TruncateAt.END).toString();
    }
}
