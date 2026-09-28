package com.metro.launcher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A named (or unnamed) group of tiles on Start. */
final class Section {
    String id = UUID.randomUUID().toString();
    String name = "";
    final List<TileSpec> tiles = new ArrayList<>();

    Section() {}

    Section(String name) {
        this.name = name == null ? "" : name;
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        JSONArray a = new JSONArray();
        for (TileSpec t : tiles) a.put(t.toJson());
        o.put("tiles", a);
        return o;
    }

    static Section fromJson(JSONObject o) throws JSONException {
        Section s = new Section(o.optString("name", ""));
        s.id = o.optString("id", s.id);
        JSONArray a = o.optJSONArray("tiles");
        if (a != null) {
            for (int i = 0; i < a.length(); i++) s.tiles.add(TileSpec.fromJson(a.getJSONObject(i)));
        }
        return s;
    }
}
