package com.metro.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Saves the Start layout (sections, tiles, dock) and builds first-run layouts. */
final class LayoutStore {
    private LayoutStore() {}

    static final String[] STYLE_NAMES = {"One section", "Two sections", "Three sections"};

    static List<Section> load(Context c) {
        String s = Prefs.sp(c).getString("layout", null);
        if (s == null) return null;
        try {
            JSONArray a = new JSONObject(s).getJSONArray("sections");
            List<Section> out = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) out.add(Section.fromJson(a.getJSONObject(i)));
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            return null;
        }
    }

    static List<String> loadDock(Context c) {
        List<String> out = new ArrayList<>();
        String s;
        try {
            s = Prefs.sp(c).getString("dockApps", "");
        } catch (Exception e) {
            s = "";
        }
        if (!s.isEmpty()) for (String k : s.split("\n")) if (!k.isEmpty()) out.add(k);
        return out;
    }

    static void save(Context c, List<Section> sections, List<String> dock) {
        JSONObject root = new JSONObject();
        try {
            JSONArray a = new JSONArray();
            for (Section s : sections) a.put(s.toJson());
            root.put("sections", a);
        } catch (Exception ignored) {
        }
        Prefs.sp(c).edit()
                .putString("layout", root.toString())
                .putString("dockApps", android.text.TextUtils.join("\n", dock))
                .apply();
    }

    /** Builds a first-run layout. style: 0 = one section, 1 = two, 2 = three. */
    static List<Section> defaults(Context c, List<AppInfo> apps, int style, int[] palette) {
        PackageManager pm = c.getPackageManager();
        Set<String> used = new HashSet<>();
        int[] ci = {0};
        Section a = new Section("");
        Section b = new Section(style == 0 ? "" : "Everyday");
        Section m = new Section(style == 2 ? "Media & tools" : "");

        addApp(a.tiles, used, pm, apps, "Phone", TileSpec.MEDIUM, palette, ci,
                new Intent(Intent.ACTION_DIAL));
        addApp(a.tiles, used, pm, apps, "Messages", TileSpec.MEDIUM, palette, ci,
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
                new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")));
        a.tiles.add(TileSpec.live(TileSpec.CLOCK, "Clock", TileSpec.WIDE, next(palette, ci)));
        a.tiles.add(TileSpec.live(TileSpec.WEATHER, "Weather", TileSpec.MEDIUM, next(palette, ci)));
        a.tiles.add(TileSpec.live(TileSpec.CALENDAR, "Calendar", TileSpec.MEDIUM, next(palette, ci)));

        addApp(b.tiles, used, pm, apps, "Browser", TileSpec.SMALL, palette, ci,
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bing.com")),
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER));
        addApp(b.tiles, used, pm, apps, "Camera", TileSpec.SMALL, palette, ci,
                new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
        addApp(b.tiles, used, pm, apps, "Photos", TileSpec.SMALL, palette, ci,
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY));
        addApp(b.tiles, used, pm, apps, "Email", TileSpec.SMALL, palette, ci,
                new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")),
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL));
        b.tiles.add(TileSpec.live(TileSpec.NOTIFS, "Notifications", TileSpec.MEDIUM, next(palette, ci)));
        addApp(b.tiles, used, pm, apps, "Maps", TileSpec.MEDIUM, palette, ci,
                new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=coffee")),
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS));
        addApp(b.tiles, used, pm, apps, "Contacts", TileSpec.SMALL, palette, ci,
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS));
        addPackage(b.tiles, used, pm, apps, "com.android.vending", "Play Store", TileSpec.SMALL, palette, ci);

        m.tiles.add(TileSpec.live(TileSpec.MEDIA, "Music", TileSpec.WIDE, next(palette, ci)));
        addApp(m.tiles, used, pm, apps, "Music", TileSpec.MEDIUM, palette, ci,
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC));
        addApp(m.tiles, used, pm, apps, "Settings", TileSpec.MEDIUM, palette, ci,
                new Intent(Settings.ACTION_SETTINGS));

        List<Section> out = new ArrayList<>();
        if (style == 0) {
            a.tiles.addAll(b.tiles);
            a.tiles.addAll(m.tiles);
            out.add(a);
        } else if (style == 1) {
            b.tiles.addAll(m.tiles);
            out.add(a);
            out.add(b);
        } else {
            out.add(a);
            out.add(b);
            out.add(m);
        }
        return out;
    }

    /** Phone, messages, browser, camera. */
    static List<String> defaultDock(Context c) {
        PackageManager pm = c.getPackageManager();
        List<String> out = new ArrayList<>();
        Intent[] intents = {
                new Intent(Intent.ACTION_DIAL),
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bing.com")),
                new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        };
        for (Intent i : intents) {
            String pkg = resolvePkg(pm, i);
            if (pkg == null) continue;
            Intent li = pm.getLaunchIntentForPackage(pkg);
            if (li == null || li.getComponent() == null) continue;
            String key = li.getComponent().getPackageName() + "/" + li.getComponent().getClassName();
            if (!out.contains(key)) out.add(key);
        }
        return out;
    }

    private static int next(int[] palette, int[] ci) {
        return palette[ci[0]++ % palette.length];
    }

    private static void addApp(List<TileSpec> out, Set<String> used, PackageManager pm,
                               List<AppInfo> apps, String generic, int size, int[] palette, int[] ci,
                               Intent... intents) {
        for (Intent intent : intents) {
            String pkg = resolvePkg(pm, intent);
            if (pkg == null || used.contains(pkg)) continue;
            if (addPackage(out, used, pm, apps, pkg, generic, size, palette, ci)) return;
        }
    }

    private static boolean addPackage(List<TileSpec> out, Set<String> used, PackageManager pm,
                                      List<AppInfo> apps, String pkg, String generic, int size,
                                      int[] palette, int[] ci) {
        if (used.contains(pkg)) return false;
        Intent li = pm.getLaunchIntentForPackage(pkg);
        if (li == null || li.getComponent() == null) return false;
        ComponentName cn = li.getComponent();
        String label = generic;
        for (AppInfo a : apps) {
            if (a.pkg.equals(cn.getPackageName())) {
                label = a.label;
                if (a.cls.equals(cn.getClassName())) break;
            }
        }
        out.add(TileSpec.app(label, cn.getPackageName(), cn.getClassName(), size, next(palette, ci)));
        used.add(pkg);
        return true;
    }

    static String resolvePkg(PackageManager pm, Intent i) {
        try {
            ResolveInfo ri = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri != null && ri.activityInfo != null) {
                String p = ri.activityInfo.packageName;
                if (!"android".equals(p) && !p.contains("resolver")) return p;
            }
            List<ResolveInfo> l = pm.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo r : l) {
                if (r.activityInfo != null) return r.activityInfo.packageName;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
