package com.metro.launcher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Alphabetical app list with Windows Phone style letter headers, plus search filtering. */
final class AppListAdapter extends BaseAdapter {
    interface Host {
        /** Icon for the list (already monochrome when that mode is on). */
        Drawable listIconFor(AppInfo a);

        int accent();

        boolean compactDrawer();

        boolean monoIcons();
    }

    static final String LETTERS = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final Context ctx;
    private final Host host;
    private final List<AppInfo> all = new ArrayList<>();
    private final List<Object> rows = new ArrayList<>();
    private String query = "";
    private final Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);

    AppListAdapter(Context ctx, Host host) {
        this.ctx = ctx;
        this.host = host;
    }

    void setApps(List<AppInfo> apps) {
        all.clear();
        all.addAll(apps);
        rebuild();
    }

    void setQuery(String q) {
        query = q == null ? "" : q.trim().toLowerCase(Locale.getDefault());
        rebuild();
    }

    boolean isSearching() {
        return !query.isEmpty();
    }

    AppInfo firstApp() {
        for (Object o : rows) if (o instanceof AppInfo) return (AppInfo) o;
        return null;
    }

    static String letterOf(String label) {
        if (label == null || label.isEmpty()) return "#";
        String n = Normalizer.normalize(label.substring(0, 1), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        if (n.isEmpty()) return "#";
        char ch = Character.toUpperCase(n.charAt(0));
        return (ch >= 'A' && ch <= 'Z') ? String.valueOf(ch) : "#";
    }

    private void rebuild() {
        rows.clear();
        if (query.isEmpty()) {
            Map<String, List<AppInfo>> groups = new LinkedHashMap<>();
            for (int i = 0; i < LETTERS.length(); i++) {
                groups.put(String.valueOf(LETTERS.charAt(i)), new ArrayList<>());
            }
            for (AppInfo a : all) groups.get(letterOf(a.label)).add(a);
            for (Map.Entry<String, List<AppInfo>> e : groups.entrySet()) {
                if (e.getValue().isEmpty()) continue;
                rows.add(e.getKey());
                rows.addAll(e.getValue());
            }
        } else {
            Locale loc = Locale.getDefault();
            // Apps whose name starts with the query come first, then any other match.
            List<AppInfo> rest = new ArrayList<>();
            for (AppInfo a : all) {
                String l = a.label.toLowerCase(loc);
                if (l.startsWith(query)) rows.add(a);
                else if (l.contains(query)) rest.add(a);
            }
            rows.addAll(rest);
        }
        notifyDataSetChanged();
    }

    Set<String> activeLetters() {
        Set<String> s = new LinkedHashSet<>();
        for (Object o : rows) if (o instanceof String) s.add((String) o);
        return s;
    }

    int positionOf(String letter) {
        for (int i = 0; i < rows.size(); i++) {
            if (letter.equals(rows.get(i))) return i;
        }
        return -1;
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public Object getItem(int position) {
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof String ? 0 : 1;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Object item = rows.get(position);
        if (item instanceof String) {
            TextView letter;
            View v = convertView;
            if (v == null) {
                LinearLayout box = new LinearLayout(ctx);
                box.setLayoutParams(new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                box.setPadding(Util.dp(ctx, 16), Util.dp(ctx, 10), Util.dp(ctx, 16), Util.dp(ctx, 6));
                letter = new TextView(ctx);
                letter.setTypeface(light);
                letter.setTextSize(24);
                letter.setTextColor(Color.WHITE);
                letter.setGravity(Gravity.START | Gravity.BOTTOM);
                letter.setPadding(Util.dp(ctx, 6), 0, 0, Util.dp(ctx, 2));
                int s = Util.dp(ctx, 46);
                box.addView(letter, new LinearLayout.LayoutParams(s, s));
                box.setTag(letter);
                v = box;
            } else {
                letter = (TextView) v.getTag();
            }
            GradientDrawable border = new GradientDrawable();
            border.setColor(Color.TRANSPARENT);
            border.setStroke(Util.dp(ctx, 2), host.accent());
            letter.setBackground(border);
            letter.setText(((String) item).toLowerCase(Locale.getDefault()));
            return v;
        }

        AppInfo a = (AppInfo) item;
        View v = convertView;
        ImageView icon;
        TextView name;
        if (v == null) {
            LinearLayout row = new LinearLayout(ctx);
            row.setLayoutParams(new AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            icon = new ImageView(ctx);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            row.addView(icon, new LinearLayout.LayoutParams(1, 1));
            name = new TextView(ctx);
            name.setTypeface(light);
            name.setTextColor(Color.WHITE);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = Util.dp(ctx, 14);
            row.addView(name, lp);
            row.setTag(new Object[]{icon, name});
            v = row;
        } else {
            Object[] tag = (Object[]) v.getTag();
            icon = (ImageView) tag[0];
            name = (TextView) tag[1];
        }
        boolean compact = host.compactDrawer();
        int vpad = Util.dp(ctx, compact ? 2 : 5);
        v.setPadding(Util.dp(ctx, 16), vpad, Util.dp(ctx, 16), vpad);
        int s = Util.dp(ctx, compact ? 34 : 46);
        ViewGroup.LayoutParams ilp = icon.getLayoutParams();
        if (ilp.width != s) {
            ilp.width = s;
            ilp.height = s;
            icon.setLayoutParams(ilp);
        }
        name.setTextSize(compact ? 17 : 21);
        if (host.monoIcons()) {
            // Classic Windows Phone look: white glyph on an accent-colored square.
            icon.setBackgroundColor(host.accent());
            int p = Util.dp(ctx, compact ? 5 : 8);
            icon.setPadding(p, p, p, p);
        } else {
            icon.setBackground(null);
            icon.setPadding(0, 0, 0, 0);
        }
        icon.setImageDrawable(host.listIconFor(a));
        name.setText(a.label);
        return v;
    }
}
