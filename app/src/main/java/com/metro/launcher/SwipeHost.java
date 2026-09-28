package com.metro.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * Two full-screen pages side by side: Start (page 0) and the app list (page 1).
 * Swipe left on Start to reach the app list; swipe right to come back.
 */
final class SwipeHost extends FrameLayout {
    interface Listener {
        void onOffset(float offset);

        void onPage(int page);
    }

    private final int slop;
    private final float flingVelocity;
    private float offset = 0f;
    private int page = 0;
    private float downX, downY, lastX;
    private boolean dragging;
    private VelocityTracker vt;
    private ValueAnimator anim;
    private Listener listener;

    SwipeHost(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        flingVelocity = 700 * c.getResources().getDisplayMetrics().density;
    }

    void setListener(Listener l) {
        listener = l;
    }

    void addPage(View v) {
        addView(v, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    int getPage() {
        return page;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        apply();
    }

    private void apply() {
        int w = getWidth();
        if (getChildCount() < 2) return;
        getChildAt(0).setTranslationX(-offset * w);
        getChildAt(1).setTranslationX((1f - offset) * w);
        getChildAt(0).setVisibility(offset >= 1f ? INVISIBLE : VISIBLE);
        getChildAt(1).setVisibility(offset <= 0f ? INVISIBLE : VISIBLE);
        if (listener != null) listener.onOffset(offset);
    }

    void goTo(int target, boolean animate) {
        if (anim != null) anim.cancel();
        if (!animate) {
            offset = target;
            page = target;
            apply();
            if (listener != null) listener.onPage(target);
            return;
        }
        final int dest = target;
        anim = ValueAnimator.ofFloat(offset, target);
        anim.setDuration(260);
        anim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
        anim.addUpdateListener(a -> {
            offset = (float) a.getAnimatedValue();
            apply();
        });
        anim.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator a) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator a) {
                if (cancelled) return;
                page = dest;
                if (listener != null) listener.onPage(dest);
            }
        });
        anim.start();
    }

    private boolean startsDrag(float dx, float dy) {
        if (Math.abs(dx) <= slop || Math.abs(dx) <= Math.abs(dy) * 1.5f) return false;
        return (page == 0 && dx < 0) || (page == 1 && dx > 0);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = ev.getX();
                downY = ev.getY();
                dragging = false;
                if (vt != null) vt.recycle();
                vt = VelocityTracker.obtain();
                vt.addMovement(ev);
                return false;
            case MotionEvent.ACTION_MOVE:
                if (vt != null) vt.addMovement(ev);
                if (!dragging && startsDrag(ev.getX() - downX, ev.getY() - downY)) {
                    dragging = true;
                    lastX = ev.getX();
                    if (anim != null) anim.cancel();
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                return dragging;
            default:
                return dragging;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (vt == null) vt = VelocityTracker.obtain();
        vt.addMovement(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = ev.getX();
                downY = ev.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) {
                    if (!startsDrag(ev.getX() - downX, ev.getY() - downY)) return true;
                    dragging = true;
                    lastX = ev.getX();
                    if (anim != null) anim.cancel();
                }
                float dx = ev.getX() - lastX;
                lastX = ev.getX();
                if (getWidth() > 0) {
                    offset = Math.max(0f, Math.min(1f, offset - dx / getWidth()));
                    apply();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    vt.computeCurrentVelocity(1000);
                    float vx = vt.getXVelocity();
                    int target;
                    if (Math.abs(vx) > flingVelocity) target = vx < 0 ? 1 : 0;
                    else target = offset > 0.5f ? 1 : 0;
                    goTo(target, true);
                }
                dragging = false;
                vt.recycle();
                vt = null;
                return true;
            default:
                return true;
        }
    }
}
