package com.metro.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A section: optional name header above its own tile grid. */
final class SectionView extends LinearLayout {
    final Section section;
    final TextView header;
    final TileGridView grid;

    SectionView(Context c, Section section) {
        super(c);
        this.section = section;
        setOrientation(VERTICAL);
        header = new TextView(c);
        header.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        header.setTextColor(Color.WHITE);
        header.setTextSize(22);
        header.setShadowLayer(6f, 0f, 1f, 0x99000000);
        header.setSingleLine(true);
        header.setLongClickable(true);
        boolean named = section.name != null && !section.name.trim().isEmpty();
        header.setText(named ? section.name.toLowerCase(java.util.Locale.getDefault()) : "");
        header.setPadding(Util.dp(c, 2), 0, 0, Util.dp(c, named ? 8 : 0));
        header.setContentDescription(named ? "Section " + section.name : "Section");
        // Unnamed sections still get a thin, pressable strip so they can be renamed or moved.
        int h = named ? ViewGroup.LayoutParams.WRAP_CONTENT : Util.dp(c, 14);
        addView(header, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        grid = new TileGridView(c);
        addView(grid, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    void addTile(View tile) {
        grid.addView(tile);
    }
}
