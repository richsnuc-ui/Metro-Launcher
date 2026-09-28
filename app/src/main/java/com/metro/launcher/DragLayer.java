package com.metro.launcher;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.FrameLayout;

/**
 * Root layout. While a tile is being dragged it takes over the whole touch stream, so the
 * scrolling Start screen and page swiping can't steal the gesture.
 */
final class DragLayer extends FrameLayout {
    interface Handler {
        boolean dragActive();

        void onDragTouch(MotionEvent ev);
    }

    private Handler handler;
    private boolean cancelSent;
    float lastRawX, lastRawY;

    DragLayer(Context c) {
        super(c);
    }

    void setHandler(Handler h) {
        handler = h;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        lastRawX = ev.getRawX();
        lastRawY = ev.getRawY();
        if (handler != null && handler.dragActive()) {
            if (!cancelSent) {
                // Tell the tile and scroll view underneath that the gesture is now ours.
                MotionEvent cancel = MotionEvent.obtain(ev);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
                cancelSent = true;
            }
            handler.onDragTouch(ev);
            return true;
        }
        cancelSent = false;
        return super.dispatchTouchEvent(ev);
    }
}
