package com.metro.launcher;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/** A real Android widget living inside a Start tile. */
final class WidgetTileView extends FrameLayout implements Tile {
    private final TileSpec spec;
    private final AppWidgetHostView hostView;
    private final int slop;
    private float downX, downY;
    private boolean longPressed;
    private final Runnable longPress = () -> {
        longPressed = true;
        performLongClick();
    };

    WidgetTileView(Context c, TileSpec spec, AppWidgetHostView hostView) {
        super(c);
        this.spec = spec;
        this.hostView = hostView;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        hostView.setPadding(0, 0, 0, 0);
        addView(hostView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setLongClickable(true);
    }

    @Override
    public TileSpec spec() {
        return spec;
    }

    void setStyle(int color, int alpha, int shape) {
        setBackgroundColor((color & 0x00FFFFFF) | (alpha << 24));
        Tile.applyShape(this, shape);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float d = getResources().getDisplayMetrics().density;
        int wd = Math.round(w / d), hd = Math.round(h / d);
        try {
            hostView.updateAppWidgetSize(null, wd, hd, wd, hd);
        } catch (Exception ignored) {
        }
    }

    /** Widgets eat touches, so watch for a long press here to open the tile menu. */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                longPressed = false;
                downX = ev.getX();
                downY = ev.getY();
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(ev.getX() - downX) > slop || Math.abs(ev.getY() - downY) > slop) {
                    removeCallbacks(longPress);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                break;
            default:
                break;
        }
        return longPressed;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int a = ev.getActionMasked();
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(longPress);
            longPressed = false;
        }
        return true;
    }
}
