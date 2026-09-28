package com.metro.launcher;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

/** Anything that can sit in a tile grid. */
interface Tile {
    TileSpec spec();

    /** Clips a tile view to the chosen tile shape (hardware-accelerated, cheap). */
    static void applyShape(View v, int shape) {
        if (shape == Prefs.SHAPE_SQUARE) {
            v.setClipToOutline(false);
            v.setOutlineProvider(ViewOutlineProvider.BOUNDS);
            return;
        }
        final float dens = v.getResources().getDisplayMetrics().density;
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline o) {
                int w = view.getWidth(), h = view.getHeight();
                float r;
                if (shape == Prefs.SHAPE_ROUNDED) r = 8 * dens;
                else if (shape == Prefs.SHAPE_SQUIRCLE) r = Math.min(w, h) * 0.24f;
                else r = Math.min(w, h) / 2f;
                o.setRoundRect(0, 0, w, h, r);
            }
        });
        v.setClipToOutline(true);
    }
}
