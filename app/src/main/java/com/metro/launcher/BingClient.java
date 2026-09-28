package com.metro.launcher;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Bing "image of the day" feed: the last 8 days of images, in portrait resolution. */
final class BingClient {
    private BingClient() {}

    static List<String> fetch() throws Exception {
        String mkt = Locale.getDefault().toLanguageTag();
        String json = Util.httpGet(
                "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=" + mkt);
        JSONArray images = new JSONObject(json).getJSONArray("images");
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < images.length(); i++) {
            JSONObject o = images.getJSONObject(i);
            String base = o.optString("urlbase", "");
            if (!base.isEmpty()) {
                urls.add("https://www.bing.com" + base + "_1080x1920.jpg");
            } else {
                String url = o.optString("url", "");
                if (!url.isEmpty()) urls.add("https://www.bing.com" + url);
            }
        }
        return urls;
    }

    static File cacheFile(File dir, String url) {
        return new File(dir, "bing_" + Integer.toHexString(url.hashCode()) + ".jpg");
    }

    /** Deletes cached images that have dropped out of the current feed. */
    static void cleanCache(File dir, List<String> keep) {
        Set<String> names = new HashSet<>();
        for (String u : keep) names.add(cacheFile(dir, u).getName());
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().startsWith("bing_") && !names.contains(f.getName())) f.delete();
        }
    }
}
