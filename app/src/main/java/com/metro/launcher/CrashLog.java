package com.metro.launcher;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Saves a crash report so it can be shown (and copied) the next time Metro opens. */
final class CrashLog {
    private CrashLog() {}

    private static boolean installed;

    static synchronized void install(Context ctx) {
        if (installed) return;
        installed = true;
        final Context app = ctx.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                write(app, thread, error);
            } catch (Throwable ignored) {
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    /** Records an error that was caught (the app kept running), unless a crash is already saved. */
    static void note(Context c, Throwable error) {
        try {
            if (file(c.getApplicationContext()).exists()) return;
            write(c.getApplicationContext(), Thread.currentThread(),
                    new RuntimeException("Caught error (Metro kept running)", error));
        } catch (Throwable ignored) {
        }
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), "last_crash.txt");
    }

    private static void write(Context c, Thread thread, Throwable error) throws Exception {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("Metro Launcher " + BuildInfo.version(c));
        pw.println("Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        pw.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")");
        pw.println("Thread: " + thread.getName());
        pw.println();
        error.printStackTrace(pw);
        pw.flush();
        try (FileOutputStream os = new FileOutputStream(file(c))) {
            os.write(sw.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /** The saved report, or null if Metro didn't crash last time. */
    static String read(Context c) {
        File f = file(c);
        if (!f.exists()) return null;
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    static long ageMs(Context c) {
        File f = file(c);
        return f.exists() ? System.currentTimeMillis() - f.lastModified() : Long.MAX_VALUE;
    }

    static void clear(Context c) {
        //noinspection ResultOfMethodCallIgnored
        file(c).delete();
    }

    /** Tiny helper so the report says which build crashed. */
    static final class BuildInfo {
        static String version(Context c) {
            try {
                return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "?";
            }
        }
    }
}
