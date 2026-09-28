package com.metro.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/** User settings, stored in SharedPreferences. */
final class Prefs {
    static final int COLOR_COLORFUL = 0, COLOR_ACCENT = 1, COLOR_MONO = 2;
    static final int SHAPE_SQUARE = 0, SHAPE_ROUNDED = 1, SHAPE_SQUIRCLE = 2, SHAPE_CIRCLE = 3;
    static final String[] SCALE_NAMES = {"Compact (more tiles)", "Normal", "Large"};
    static final String[] SHAPE_NAMES = {"Square (classic Metro)", "Rounded", "Squircle", "Circle / pill"};
    static final String[] COLOR_MODE_NAMES = {"Colorful (each tile its own color)", "One accent color", "Monochrome"};

    boolean setupDone = false;
    int scale = 1;                // 0 compact, 1 normal, 2 large
    int shape = SHAPE_SQUARE;
    int colorMode = COLOR_COLORFUL;
    int palette = 0;
    int accent = 0xFF1BA1E2;
    boolean monoIcons = false;
    int tileAlpha = 215;          // 90..255
    boolean dock = false;
    boolean compactDrawer = false;
    boolean bing = true;
    int bingMins = 60;            // 0 = today's image only
    boolean celsius = false;
    String city = "";
    double cityLat = Double.NaN, cityLon = Double.NaN;
    String cityName = "";
    boolean askedPerms = false, askedRole = false;
    String bingCache = "";
    long bingFetched = 0;

    boolean effectiveMonoIcons() {
        return monoIcons || colorMode == COLOR_MONO;
    }

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("metro", Context.MODE_PRIVATE);
    }

    static Prefs load(Context c) {
        SharedPreferences p = sp(c);
        Prefs s = new Prefs();
        String country = Locale.getDefault().getCountry();
        boolean usesF = "US".equals(country) || "LR".equals(country) || "MM".equals(country);
        s.setupDone = p.getBoolean("setupDone", false);
        s.scale = p.getInt("scale", 1);
        s.shape = p.getInt("shape", SHAPE_SQUARE);
        s.colorMode = p.getInt("colorMode", COLOR_COLORFUL);
        s.palette = p.getInt("palette", 0);
        s.accent = p.getInt("accent", 0xFF1BA1E2);
        s.monoIcons = p.getBoolean("monoIcons", false);
        s.tileAlpha = p.getInt("tileAlpha", 215);
        s.dock = p.getBoolean("dock", false);
        s.compactDrawer = p.getBoolean("compactDrawer", false);
        s.bing = p.getBoolean("bing", true);
        s.bingMins = p.getInt("bingMins", 60);
        s.celsius = p.getBoolean("celsius", !usesF);
        s.city = p.getString("city", "");
        s.cityLat = Double.longBitsToDouble(p.getLong("cityLat", Double.doubleToLongBits(Double.NaN)));
        s.cityLon = Double.longBitsToDouble(p.getLong("cityLon", Double.doubleToLongBits(Double.NaN)));
        s.cityName = p.getString("cityName", "");
        s.askedPerms = p.getBoolean("askedPerms", false);
        s.askedRole = p.getBoolean("askedRole", false);
        s.bingCache = p.getString("bingCache", "");
        s.bingFetched = p.getLong("bingFetched", 0);
        if (s.palette < 0 || s.palette >= Util.PALETTES.length) s.palette = 0;
        return s;
    }

    void save(Context c) {
        sp(c).edit()
                .putBoolean("setupDone", setupDone)
                .putInt("scale", scale)
                .putInt("shape", shape)
                .putInt("colorMode", colorMode)
                .putInt("palette", palette)
                .putInt("accent", accent)
                .putBoolean("monoIcons", monoIcons)
                .putInt("tileAlpha", tileAlpha)
                .putBoolean("dock", dock)
                .putBoolean("compactDrawer", compactDrawer)
                .putBoolean("bing", bing)
                .putInt("bingMins", bingMins)
                .putBoolean("celsius", celsius)
                .putString("city", city)
                .putLong("cityLat", Double.doubleToLongBits(cityLat))
                .putLong("cityLon", Double.doubleToLongBits(cityLon))
                .putString("cityName", cityName)
                .putBoolean("askedPerms", askedPerms)
                .putBoolean("askedRole", askedRole)
                .putString("bingCache", bingCache)
                .putLong("bingFetched", bingFetched)
                .apply();
    }
}
