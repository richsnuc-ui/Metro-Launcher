package com.metro.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays tiles out on a grid, filling gaps first-fit like the Windows Phone Start screen.
 * The parent (StartLayout) decides the column count and cell size.
 */
final class TileGridView extends ViewGroup {
    private final int gap;
    private int cols = 4;
    private int unit;
    private int[] col = new int[0], row = new int[0];

    TileGridView(Context c) {
        super(c);
        gap = Util.dp(c, 6);
        setClipChildren(false);
        setClipToPadding(false);
    }

    int gap() {
        return gap;
    }

    /** Called by StartLayout during its own measure pass, right before it measures this grid. */
    void setGrid(int cols, int unit) {
        this.cols = Math.max(1, cols);
        this.unit = unit;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int u = unit;
        if (u <= 0) {
            int avail = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
            u = Math.max(1, (avail - gap * (cols - 1)) / cols);
        }
        int n = getChildCount();
        col = new int[n];
        row = new int[n];
        List<boolean[]> occ = new ArrayList<>();
        int maxRow = 0;
        for (int i = 0; i < n; i++) {
            View child = getChildAt(i);
            TileSpec s = ((Tile) child).spec();
            int sw = Math.min(cols, s.spanW());
            int sh = Math.min(s.spanH(), s.size == TileSpec.LARGE ? sw : s.spanH());
            placed:
            for (int r = 0; ; r++) {
                for (int c = 0; c <= cols - sw; c++) {
                    if (fits(occ, r, c, sw, sh)) {
                        for (int rr = r; rr < r + sh; rr++)
                            for (int cc = c; cc < c + sw; cc++) occ.get(rr)[cc] = true;
                        col[i] = c;
                        row[i] = r;
                        maxRow = Math.max(maxRow, r + sh);
                        break placed;
                    }
                }
            }
            int w = sw * u + (sw - 1) * gap;
            int h = sh * u + (sh - 1) * gap;
            child.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
        unitUsed = u;
        int width = getPaddingLeft() + getPaddingRight() + cols * u + (cols - 1) * gap;
        int height = getPaddingTop() + getPaddingBottom()
                + (maxRow > 0 ? maxRow * u + (maxRow - 1) * gap : 0);
        setMeasuredDimension(resolveSize(width, widthSpec), height);
    }

    private int unitUsed;

    private boolean fits(List<boolean[]> occ, int r, int c, int sw, int sh) {
        for (int rr = r; rr < r + sh; rr++) {
            while (occ.size() <= rr) occ.add(new boolean[cols]);
            for (int cc = c; cc < c + sw; cc++) {
                if (occ.get(rr)[cc]) return false;
            }
        }
        return true;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int n = Math.min(getChildCount(), col.length);
        for (int i = 0; i < n; i++) {
            View v = getChildAt(i);
            int x = getPaddingLeft() + col[i] * (unitUsed + gap);
            int y = getPaddingTop() + row[i] * (unitUsed + gap);
            v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
        }
    }
}
