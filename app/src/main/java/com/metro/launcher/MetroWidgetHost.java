package com.metro.launcher;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.widget.RemoteViews;

/** Widget host that creates views able to report why a widget failed to draw. */
final class MetroWidgetHost extends AppWidgetHost {
    MetroWidgetHost(Context context, int hostId) {
        super(context, hostId);
    }

    @Override
    protected AppWidgetHostView onCreateView(Context context, int appWidgetId, AppWidgetProviderInfo appWidget) {
        return new MetroWidgetHostView(context);
    }

    static final class MetroWidgetHostView extends AppWidgetHostView {
        private boolean checked;
        Throwable error;

        MetroWidgetHostView(Context context) {
            super(context);
        }

        @Override
        public void updateAppWidget(RemoteViews remoteViews) {
            super.updateAppWidget(remoteViews);
            if (remoteViews != null && !checked) {
                checked = true;
                // Inflate once more ourselves, only to capture the reason if it fails.
                try {
                    remoteViews.apply(getContext(), this);
                } catch (Throwable t) {
                    error = t;
                }
            }
        }
    }
}
