package com.metro.launcher;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Arranges sections. On a phone they stack vertically; on wider screens (foldables, tablets,
 * landscape) sections sit side by side in columns separated by a horizontal gap, flowing
 * top-to-bottom into whichever column is shortest.
 */
final class StartLayout extends ViewGroup {
    private int scale = 1;
    private final int sectionGapH, sectionGapV;
    private int[] xs = new int[0], ys = new int[0];

    StartLayout(Context c) {
        super(c);
        sectionGapH = Util.dp(c, 36);
        sectionGapV = Util.dp(c, 22);
        setClipChildren(false);
    }

    void setScale(int scale) {
        if (this.scale != scale) {
            this.scale = scale;
            requestLayout();
        }
    }

    /** Tiles per section row for each scale: compact 6, normal 4, large 3. */
    int columns() {
        return scale == 0 ? 6 : scale == 2 ? 3 : 4;
    }

    private int targetUnitDp() {
        return scale == 0 ? 62 : scale == 2 ? 108 : 84;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int avail = Math.max(1, width - getPaddingLeft() - getPaddingRight());
        int count = getChildCount();
        int cols = columns();
        float d = getResources().getDisplayMetrics().density;
        int target = Math.round(targetUnitDp() * d);
        int gap = Util.dp(getContext(), 6);
        int natural = cols * target + (cols - 1) * gap;

        int n = (int) ((avail + sectionGapH) / (natural * 0.8f + sectionGapH));
        n = Math.max(1, Math.min(n, Math.max(1, count)));
        int sectionW = (avail - (n - 1) * sectionGapH) / n;
        int unit = (sectionW - (cols - 1) * gap) / cols;
        int maxUnit = Math.round(target * 1.35f);
        if (unit > maxUnit) {
            unit = maxUnit;
            sectionW = cols * unit + (cols - 1) * gap;
        }
        int totalW = n * sectionW + (n - 1) * sectionGapH;
        int x0 = getPaddingLeft() + (avail - totalW) / 2;

        xs = new int[count];
        ys = new int[count];
        int[] colH = new int[n];
        for (int i = 0; i < count; i++) {
            View v = getChildAt(i);
            if (v instanceof SectionView) ((SectionView) v).grid.setGrid(cols, unit);
            v.measure(MeasureSpec.makeMeasureSpec(sectionW, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int k = 0;
            for (int j = 1; j < n; j++) if (colH[j] < colH[k]) k = j;
            xs[i] = x0 + k * (sectionW + sectionGapH);
            ys[i] = getPaddingTop() + colH[k];
            colH[k] += v.getMeasuredHeight() + sectionGapV;
        }
        int maxH = 0;
        for (int h : colH) maxH = Math.max(maxH, h);
        if (count > 0) maxH -= sectionGapV;
        setMeasuredDimension(width, getPaddingTop() + Math.max(0, maxH) + getPaddingBottom());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int n = Math.min(getChildCount(), xs.length);
        for (int i = 0; i < n; i++) {
            View v = getChildAt(i);
            v.layout(xs[i], ys[i], xs[i] + v.getMeasuredWidth(), ys[i] + v.getMeasuredHeight());
        }
    }
}
