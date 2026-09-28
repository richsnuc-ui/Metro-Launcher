package com.metro.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import java.util.List;

/** Tracks whichever app is playing media, for the Music tile. */
final class MediaWatcher {
    private final Context ctx;
    private final Runnable onChange;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaSessionManager msm;
    private MediaController controller;
    private boolean started;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener = this::pick;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            onChange.run();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            onChange.run();
        }

        @Override
        public void onSessionDestroyed() {
            detach();
            onChange.run();
        }
    };

    MediaWatcher(Context ctx, Runnable onChange) {
        this.ctx = ctx.getApplicationContext();
        this.onChange = onChange;
    }

    /** Returns false if notification access hasn't been granted yet. */
    boolean start() {
        if (started) return true;
        if (!NotifService.isEnabled(ctx)) return false;
        try {
            msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm == null) return false;
            msm.addOnActiveSessionsChangedListener(sessionsListener, NotifService.component(ctx), main);
            pick(msm.getActiveSessions(NotifService.component(ctx)));
            started = true;
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    void stop() {
        if (msm != null) {
            try {
                msm.removeOnActiveSessionsChangedListener(sessionsListener);
            } catch (Exception ignored) {
            }
        }
        detach();
        started = false;
    }

    private void pick(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController c : list) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                    best = c;
                    break;
                }
                if (best == null) best = c;
            }
        }
        if (controller != null && best != null
                && controller.getSessionToken().equals(best.getSessionToken())) {
            onChange.run();
            return;
        }
        detach();
        controller = best;
        if (controller != null) controller.registerCallback(callback, main);
        onChange.run();
    }

    private void detach() {
        if (controller != null) {
            try {
                controller.unregisterCallback(callback);
            } catch (Exception ignored) {
            }
        }
        controller = null;
    }

    boolean hasSession() {
        return controller != null && controller.getMetadata() != null;
    }

    boolean isPlaying() {
        PlaybackState s = controller == null ? null : controller.getPlaybackState();
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }

    String title() {
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        if (m == null) return "";
        String t = m.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (t == null) t = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
        return t == null ? "" : t;
    }

    String artist() {
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        if (m == null) return "";
        String a = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (a == null) a = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        if (a == null) a = m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
        return a == null ? "" : a;
    }

    Bitmap art() {
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        if (m == null) return null;
        Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        return b;
    }

    String packageName() {
        return controller == null ? null : controller.getPackageName();
    }

    void togglePlay() {
        if (controller == null) return;
        if (isPlaying()) controller.getTransportControls().pause();
        else controller.getTransportControls().play();
    }

    void next() {
        if (controller != null) controller.getTransportControls().skipToNext();
    }
}
