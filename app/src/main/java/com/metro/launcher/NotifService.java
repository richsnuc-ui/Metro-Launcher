package com.metro.launcher;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects notifications for the Notifications tile ("notification folder") and gives the
 * Music tile access to media sessions. The user must grant Notification access.
 */
public class NotifService extends NotificationListenerService {
    static volatile NotifService instance;
    private static Runnable onChange;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void setOnChange(Runnable r) {
        onChange = r;
    }

    static ComponentName component(Context c) {
        return new ComponentName(c, NotifService.class);
    }

    static boolean isEnabled(Context c) {
        String flat = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(component(c).flattenToString());
    }

    private static void changed() {
        MAIN.post(() -> {
            Runnable r = onChange;
            if (r != null) r.run();
        });
    }

    @Override
    public void onListenerConnected() {
        instance = this;
        changed();
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
        changed();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        changed();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        changed();
    }

    /** Clearable notifications, newest first (no ongoing or group-summary entries). */
    static List<StatusBarNotification> items() {
        NotifService s = instance;
        List<StatusBarNotification> out = new ArrayList<>();
        if (s == null) return out;
        try {
            StatusBarNotification[] all = s.getActiveNotifications();
            if (all == null) return out;
            for (StatusBarNotification n : all) {
                if (n.isOngoing() || !n.isClearable()) continue;
                if ((n.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                if (s.getPackageName().equals(n.getPackageName())) continue;
                out.add(n);
            }
        } catch (Exception ignored) {
        }
        Collections.sort(out, (a, b) -> Long.compare(b.getPostTime(), a.getPostTime()));
        return out;
    }

    static String title(StatusBarNotification n) {
        CharSequence t = n.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE);
        return t == null ? "" : t.toString();
    }

    static String text(StatusBarNotification n) {
        CharSequence t = n.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT);
        return t == null ? "" : t.toString();
    }

    static void clearAll() {
        NotifService s = instance;
        if (s != null) {
            try {
                s.cancelAllNotifications();
            } catch (Exception ignored) {
            }
        }
    }

    static void dismiss(StatusBarNotification n) {
        NotifService s = instance;
        if (s != null) {
            try {
                s.cancelNotification(n.getKey());
            } catch (Exception ignored) {
            }
        }
    }
}
