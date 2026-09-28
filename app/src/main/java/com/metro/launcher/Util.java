package com.metro.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.TypedValue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Small helpers shared across the launcher. */
final class Util {
    private Util() {}

    /** The classic Windows Phone accent colors. */
    static final int[] PALETTE = {
            0xFFA4C400, 0xFF60A917, 0xFF008A00, 0xFF00ABA9, 0xFF1BA1E2,
            0xFF0050EF, 0xFF6A00FF, 0xFFAA00FF, 0xFFF472D0, 0xFFD80073,
            0xFFA20025, 0xFFE51400, 0xFFFA6800, 0xFFF0A30A, 0xFFE3C800,
            0xFF825A2C, 0xFF6D8764, 0xFF647687, 0xFF76608A, 0xFF87794E
    };
    static final String[] PALETTE_NAMES = {
            "Lime", "Green", "Emerald", "Teal", "Cyan",
            "Cobalt", "Indigo", "Violet", "Pink", "Magenta",
            "Crimson", "Red", "Orange", "Amber", "Yellow",
            "Brown", "Olive", "Steel", "Mauve", "Taupe"
    };

    /** Global color palettes; the first is the classic Windows Phone set. */
    static final String[] PALETTE_SET_NAMES = {"Windows Phone", "Soft pastel", "Earth", "Neon", "Ocean"};
    static final int[][] PALETTES = {
            PALETTE,
            {0xFF6C9BD2, 0xFF7FC8A9, 0xFFE8B86D, 0xFFD98E8E, 0xFFA78BD1,
                    0xFF6FB7C4, 0xFFC7A27C, 0xFF8FA98B, 0xFFD69BB8, 0xFF9AA5B8},
            {0xFF8C5A2B, 0xFF6D8764, 0xFFA0522D, 0xFF556B2F, 0xFFB8860B,
                    0xFF7B6A4E, 0xFF4F6D5B, 0xFF9C6644, 0xFF6B4E3D, 0xFF8A7F4B},
            {0xFFFF2D95, 0xFF00E5FF, 0xFF7CFF00, 0xFFFFB300, 0xFFB000FF,
                    0xFF00FF9C, 0xFFFF3D00, 0xFF2979FF, 0xFFFFEA00, 0xFFFF1744},
            {0xFF01579B, 0xFF0277BD, 0xFF00838F, 0xFF006064, 0xFF1565C0,
                    0xFF00695C, 0xFF283593, 0xFF0097A7, 0xFF004D40, 0xFF1A237E},
    };

    static String colorName(int color) {
        for (int i = 0; i < PALETTE.length; i++) {
            if ((PALETTE[i] & 0xFFFFFF) == (color & 0xFFFFFF)) return PALETTE_NAMES[i];
        }
        return "Custom";
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static float sp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                c.getResources().getDisplayMetrics());
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "MetroLauncher/1.0 (Android)");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            conn.disconnect();
            throw new IOException("HTTP " + code + " for " + url);
        }
        return conn;
    }

    static String httpGet(String url) throws IOException {
        HttpURLConnection conn = open(url);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    static void download(String url, File out) throws IOException {
        File tmp = new File(out.getPath() + ".part");
        HttpURLConnection conn = open(url);
        try (InputStream in = conn.getInputStream(); OutputStream os = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        } finally {
            conn.disconnect();
        }
        if (!tmp.renameTo(out)) {
            tmp.delete();
            throw new IOException("Could not save " + out);
        }
    }

    /** Decodes an image file, scaled down to roughly the requested size to save memory. */
    static Bitmap decodeSampled(File f, int reqW, int reqH) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= reqW && o.outHeight / (sample * 2) >= reqH) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getPath(), d);
    }
}
