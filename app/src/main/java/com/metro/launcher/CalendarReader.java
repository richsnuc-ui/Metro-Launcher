package com.metro.launcher;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Finds the next upcoming calendar event for the Calendar live tile. */
final class CalendarReader {
    private CalendarReader() {}

    static boolean allowed(Context c) {
        return c.checkSelfPermission(Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Returns {title, when} or null. */
    static String[] next(Context c) {
        if (!allowed(c)) return null;
        long now = System.currentTimeMillis();
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, now - 24 * 3600000L);
        ContentUris.appendId(b, now + 3 * 86400000L);
        String[] proj = {
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY
        };
        try (Cursor cur = c.getContentResolver().query(b.build(), proj, null, null,
                CalendarContract.Instances.BEGIN + " ASC")) {
            if (cur == null) return null;
            while (cur.moveToNext()) {
                String title = cur.getString(0);
                if (title == null || title.trim().isEmpty()) title = "(No title)";
                long begin = cur.getLong(1);
                long end = cur.getLong(2);
                boolean allDay = cur.getInt(3) != 0;
                if (allDay) {
                    // All-day events are stored as UTC midnights; convert to local days.
                    Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    u.setTimeInMillis(begin);
                    Calendar local = Calendar.getInstance();
                    local.clear();
                    local.set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH));
                    long localStart = local.getTimeInMillis();
                    long localEnd = localStart + (end - begin);
                    if (localEnd <= now) continue;
                    String day = localStart <= now ? "Today" : dayLabel(localStart);
                    return new String[]{title, day + " · All day"};
                }
                if (end <= now) continue;
                String when = begin <= now ? "Now"
                        : dayLabel(begin) + " " + DateFormat.getTimeFormat(c).format(new Date(begin));
                return new String[]{title, when};
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static String dayLabel(long millis) {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        long start = today.getTimeInMillis();
        long days = (millis - start) / 86400000L;
        if (millis < start) return "Today";
        if (days == 0) return "Today";
        if (days == 1) return "Tomorrow";
        return new SimpleDateFormat("EEEE", Locale.getDefault()).format(new Date(millis));
    }
}
