package com.metro.launcher;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * A deliberately bare screen (no tiles, no network, no location) that shows the last crash
 * report so it can be copied, even when the Start screen itself keeps crashing.
 */
public class CrashReportActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String report = CrashLog.read(this);
        int pad = Util.dp(this, 16);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Color.BLACK);
        box.setPadding(pad, Util.dp(this, 40), pad, Util.dp(this, 24));

        TextView title = new TextView(this);
        title.setText("Metro closed unexpectedly");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        box.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Tap Copy report, then paste it to Claude so the exact problem can be fixed.");
        hint.setTextColor(0xFFCCCCCC);
        hint.setTextSize(15);
        hint.setPadding(0, Util.dp(this, 8), 0, Util.dp(this, 12));
        box.addView(hint);

        Button copy = new Button(this);
        copy.setText("Copy report");
        copy.setAllCaps(false);
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null && report != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Metro crash report", report));
                Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show();
            }
        });
        box.addView(copy);

        Button retry = new Button(this);
        retry.setText("Try opening Metro again");
        retry.setAllCaps(false);
        retry.setOnClickListener(v -> {
            CrashLog.clear(this);
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
        box.addView(retry);

        Button home = new Button(this);
        home.setText("Switch back to my old home screen");
        home.setAllCaps(false);
        home.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        box.addView(home);

        TextView tv = new TextView(this);
        tv.setText(report == null ? "(No report saved.)" : report);
        tv.setTextColor(0xFFDDDDDD);
        tv.setTextSize(11);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        tv.setPadding(0, Util.dp(this, 16), 0, 0);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(box);
    }
}
