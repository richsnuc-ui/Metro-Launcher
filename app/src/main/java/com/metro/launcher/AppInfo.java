package com.metro.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An installed, launchable app. */
final class AppInfo {
    final String label, pkg, cls;
    final ActivityInfo ai;

    AppInfo(String label, String pkg, String cls, ActivityInfo ai) {
        this.label = label;
        this.pkg = pkg;
        this.cls = cls;
        this.ai = ai;
    }

    String key() {
        return pkg + "/" + cls;
    }

    static List<AppInfo> loadAll(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(i, 0);
        List<AppInfo> out = new ArrayList<>();
        String self = c.getPackageName();
        for (ResolveInfo ri : ris) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || self.equals(ai.packageName)) continue;
            CharSequence l = ri.loadLabel(pm);
            String label = l == null ? ai.packageName : l.toString().trim();
            out.add(new AppInfo(label, ai.packageName, ai.name, ai));
        }
        final Collator col = Collator.getInstance();
        col.setStrength(Collator.PRIMARY);
        Collections.sort(out, (a, b) -> col.compare(a.label, b.label));
        return out;
    }
}
