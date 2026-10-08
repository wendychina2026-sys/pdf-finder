package com.example.twobuttons;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class SettingsActivity extends BaseActivity {

    private static final String[] NAMES = {"Light", "Dark", "System default"};
    private static final String[] VALUES = {AppTheme.LIGHT, AppTheme.DARK, AppTheme.SYSTEM};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Settings");

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(AppTheme.bg(this));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = CaseViews.dp(this, 16);
        root.setPadding(p, p, p, p);

        TextView head = CaseViews.text(this, "Theme", 14, AppTheme.accent(this), true);
        head.setPadding(0, CaseViews.dp(this, 8), 0, CaseViews.dp(this, 8));
        root.addView(head);

        int idx = indexOf(AppTheme.mode(this));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(CaseViews.dp(this, 16), CaseViews.dp(this, 14),
                CaseViews.dp(this, 16), CaseViews.dp(this, 14));
        row.setBackground(CaseViews.box(this, AppTheme.card(this), 12, AppTheme.cardStroke(this), 1));
        row.addView(CaseViews.text(this, "Choose theme", 16, AppTheme.text(this), true));
        row.addView(CaseViews.text(this, NAMES[idx], 14, AppTheme.sub(this), false));
        row.setOnClickListener(v -> showDialog());
        root.addView(row, CaseViews.lp(this, 0, 0));

        sv.addView(root);
        setContentView(sv);
    }

    private int indexOf(String mode) {
        for (int i = 0; i < VALUES.length; i++) if (VALUES[i].equals(mode)) return i;
        return 0;
    }

    private void showDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Theme")
                .setSingleChoiceItems(NAMES, indexOf(AppTheme.mode(this)), (d, which) -> {
                    AppTheme.setMode(this, VALUES[which]);
                    d.dismiss();
                    recreate();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
