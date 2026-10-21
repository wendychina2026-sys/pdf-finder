package com.example.twobuttons;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class SettingsActivity extends BaseActivity {

    @Override
    protected int navIndex() { return 4; }

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

        TextView support = CaseViews.text(this, "Support", 14, AppTheme.accent(this), true);
        support.setPadding(0, CaseViews.dp(this, 24), 0, CaseViews.dp(this, 8));
        root.addView(support);

        LinearLayout contact = new LinearLayout(this);
        contact.setOrientation(LinearLayout.VERTICAL);
        contact.setGravity(Gravity.CENTER_VERTICAL);
        contact.setPadding(CaseViews.dp(this, 16), CaseViews.dp(this, 14),
                CaseViews.dp(this, 16), CaseViews.dp(this, 14));
        contact.setBackground(CaseViews.box(this, AppTheme.card(this), 12, AppTheme.cardStroke(this), 1));
        contact.addView(CaseViews.text(this, "Contact us", 16, AppTheme.text(this), true));
        contact.addView(CaseViews.text(this, "Get in touch with support", 14, AppTheme.sub(this), false));
        contact.setOnClickListener(v -> startActivity(new Intent(this, ContactUsActivity.class)));
        root.addView(contact, CaseViews.lp(this, 0, 0));

        sv.addView(root);
        setContentView(sv);
    }

    private int indexOf(String mode) {
        for (int i = 0; i < VALUES.length; i++) if (VALUES[i].equals(mode)) return i;
        return 0;
    }

    private void showDialog() {
        PickerDialogs.optionSheet(this, "Theme", null, java.util.Arrays.asList(NAMES),
                indexOf(AppTheme.mode(this)), which -> {
                    AppTheme.setMode(this, VALUES[which]);
                    recreate();
                });
    }
}
