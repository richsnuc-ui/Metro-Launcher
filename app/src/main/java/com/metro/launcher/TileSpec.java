package com.metro.launcher;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/** One tile on the Start screen. */
final class TileSpec {
    static final int APP = 0, CLOCK = 1, WEATHER = 2, CALENDAR = 3, MEDIA = 4, NOTIFS = 5, WIDGET = 6;
    static final int SMALL = 1, MEDIUM = 2, WIDE = 3, LARGE = 4;

    String id;
    int type;
    String pkg = "", cls = "", label = "";
    int size;
    int color;
    int widgetId = -1;

    static TileSpec app(String label, String pkg, String cls, int size, int color) {
        TileSpec t = base(APP, label, size, color);
        t.pkg = pkg;
        t.cls = cls;
        return t;
    }

    static TileSpec live(int type, String label, int size, int color) {
        return base(type, label, size, color);
    }

    static TileSpec widget(int widgetId, String label, int size) {
        TileSpec t = base(WIDGET, label, size, 0xFF000000);
        t.widgetId = widgetId;
        return t;
    }

    private static TileSpec base(int type, String label, int size, int color) {
        TileSpec t = new TileSpec();
        t.id = UUID.randomUUID().toString();
        t.type = type;
        t.label = label;
        t.size = size;
        t.color = color;
        return t;
    }

    static String liveLabel(int type) {
        switch (type) {
            case CLOCK: return "Clock";
            case WEATHER: return "Weather";
            case CALENDAR: return "Calendar";
            case MEDIA: return "Music";
            case NOTIFS: return "Notifications";
            default: return "";
        }
    }

    int spanW() {
        return size == SMALL ? 1 : size == MEDIUM ? 2 : 4;
    }

    int spanH() {
        return size == SMALL ? 1 : size == LARGE ? 4 : 2;
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("type", type);
        o.put("pkg", pkg);
        o.put("cls", cls);
        o.put("label", label);
        o.put("size", size);
        o.put("color", color);
        o.put("widgetId", widgetId);
        return o;
    }

    static TileSpec fromJson(JSONObject o) {
        TileSpec t = new TileSpec();
        t.id = o.optString("id", UUID.randomUUID().toString());
        t.type = o.optInt("type", APP);
        t.pkg = o.optString("pkg", "");
        t.cls = o.optString("cls", "");
        t.label = o.optString("label", "");
        t.size = o.optInt("size", MEDIUM);
        t.color = o.optInt("color", 0xFF1BA1E2);
        t.widgetId = o.optInt("widgetId", -1);
        return t;
    }
}
