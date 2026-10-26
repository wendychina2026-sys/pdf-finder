package com.example.twobuttons;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Startup popup: shows the newest change-log entry once after the installed version changes.
 * Fresh install: version is recorded silently, no popup. Same version: no popup.
 */
final class WhatsNew {
    private static final String PREFS = "app_runtime";
    private static final String KEY = "last_seen_version_code";

    private WhatsNew() {}

    static void showIfNewVersion(Activity a) {
        PackageInfo p = ApkInstaller.installed(a);
        if (p == null || ChangeLog.ENTRIES.length == 0) return;
        long now = ApkInstaller.code(p);
        SharedPreferences sp = a.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long last = sp.getLong(KEY, -1);
        if (last == now) return;
        sp.edit().putLong(KEY, now).apply();   // mark seen first: popup never repeats, even if dismissed oddly
        if (last == -1) return;                // fresh install
        if (last > now) return;                // downgrade: nothing new
        show(a, p);
    }

    private static void show(Activity a, PackageInfo p) {
        ChangeLog.Entry e = ChangeLog.ENTRIES[0];
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        TextView t = CaseViews.text(a, e.title, 16, AppTheme.text(a), true);
        t.setPadding(0, CaseViews.dp(a, 4), 0, CaseViews.dp(a, 6));
        body.addView(t);
        for (String item : e.items) {
            TextView row = CaseViews.text(a, "\u2022  " + item, 13, AppTheme.text(a), false);
            row.setPadding(0, CaseViews.dp(a, 3), 0, CaseViews.dp(a, 3));
            body.addView(row);
        }
        String sub = "Version " + p.versionName + " (build " + ApkInstaller.code(p) + ") \u2022 " + e.date;
        PickerDialogs.formSheet(a, "What's new", sub, body, "Full change log", () -> {
            a.startActivity(new Intent(a, ChangeLogActivity.class));
            return true;
        }, null);
    }
}
