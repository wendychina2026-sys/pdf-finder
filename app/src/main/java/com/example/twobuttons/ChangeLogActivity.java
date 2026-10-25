package com.example.twobuttons;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Settings > Change log: what each update added, newest first. */
public class ChangeLogActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Change log");

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(AppTheme.bg(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = CaseViews.dp(this, 16);
        root.setPadding(p, p, p, p);

        TextView head = CaseViews.text(this, "What's new", 20, AppTheme.text(this), true);
        root.addView(head);
        TextView ver = CaseViews.text(this, "Installed version " + ApkInstaller.installedLabel(this), 13,
                AppTheme.sub(this), false);
        ver.setPadding(0, CaseViews.dp(this, 2), 0, CaseViews.dp(this, 8));
        root.addView(ver);

        for (int i = 0; i < ChangeLog.ENTRIES.length; i++) {
            ChangeLog.Entry e = ChangeLog.ENTRIES[i];
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(CaseViews.dp(this, 14), CaseViews.dp(this, 12), CaseViews.dp(this, 14), CaseViews.dp(this, 12));
            card.setBackground(CaseViews.box(this, AppTheme.card(this), 12,
                    i == 0 ? AppTheme.accent(this) : AppTheme.cardStroke(this), i == 0 ? 2 : 1));

            LinearLayout top = new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView date = CaseViews.text(this, e.date, 12, AppTheme.accent(this), true);
            top.addView(date);
            if (i == 0) {
                TextView latest = CaseViews.text(this, "  \u2022 LATEST", 11, AppTheme.accent(this), true);
                top.addView(latest);
            }
            card.addView(top);
            TextView title = CaseViews.text(this, e.title, 16, AppTheme.text(this), true);
            title.setPadding(0, CaseViews.dp(this, 2), 0, CaseViews.dp(this, 6));
            card.addView(title);
            for (String item : e.items) {
                TextView t = CaseViews.text(this, "\u2022  " + item, 13, AppTheme.text(this), false);
                t.setPadding(0, CaseViews.dp(this, 3), 0, CaseViews.dp(this, 3));
                card.addView(t);
            }
            root.addView(card, CaseViews.lp(this, 0, 10));
        }
        sv.addView(root);
        setContentView(sv);
    }
}
