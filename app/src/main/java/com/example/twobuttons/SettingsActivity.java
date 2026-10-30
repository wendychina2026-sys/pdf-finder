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

        TextView app = CaseViews.text(this, "App", 14, AppTheme.accent(this), true);
        app.setPadding(0, CaseViews.dp(this, 24), 0, CaseViews.dp(this, 8));
        root.addView(app);

        LinearLayout news = new LinearLayout(this);
        news.setOrientation(LinearLayout.VERTICAL);
        news.setGravity(Gravity.CENTER_VERTICAL);
        news.setPadding(CaseViews.dp(this, 16), CaseViews.dp(this, 14),
                CaseViews.dp(this, 16), CaseViews.dp(this, 14));
        news.setBackground(CaseViews.box(this, AppTheme.card(this), 12, AppTheme.cardStroke(this), 1));
        news.addView(CaseViews.text(this, "Legal News", 16, AppTheme.text(this), true));
        news.addView(CaseViews.text(this, "Latest Supreme Court, High Court and tribunal news from Supreme Today, as cards. Needs internet.",
                14, AppTheme.sub(this), false));
        news.setOnClickListener(v -> startActivity(new Intent(this, NewsActivity.class)));
        root.addView(news, CaseViews.lp(this, 0, 0));

        LinearLayout gh = new LinearLayout(this);
        gh.setOrientation(LinearLayout.VERTICAL);
        gh.setGravity(Gravity.CENTER_VERTICAL);
        gh.setPadding(CaseViews.dp(this, 16), CaseViews.dp(this, 14),
                CaseViews.dp(this, 16), CaseViews.dp(this, 14));
        gh.setBackground(CaseViews.box(this, AppTheme.card(this), 12, AppTheme.cardStroke(this), 1));
        gh.addView(CaseViews.text(this, "Check for update", 16, AppTheme.text(this), true));
        gh.addView(CaseViews.text(this, "Looks on GitHub for a newer version, downloads it and opens the installer. Needs internet.",
                14, AppTheme.sub(this), false));
        gh.setOnClickListener(v -> UpdateChecker.check(this));
        root.addView(gh, CaseViews.lp(this, 8, 0));

        LinearLayout upd = new LinearLayout(this);
        upd.setOrientation(LinearLayout.VERTICAL);
        upd.setGravity(Gravity.CENTER_VERTICAL);
        upd.setPadding(CaseViews.dp(this, 16), CaseViews.dp(this, 14),
                CaseViews.dp(this, 16), CaseViews.dp(this, 14));
        upd.setBackground(CaseViews.box(this, AppTheme.card(this), 12, AppTheme.cardStroke(this), 1));
        upd.addView(CaseViews.text(this, "Install update from APK file", 16, AppTheme.text(this), true));
        upd.addView(CaseViews.text(this, "Installed version " + ApkInstaller.installedLabel(this)
                + ". Warns if the APK is the same or older.", 14, AppTheme.sub(this), false));
        upd.setOnClickListener(v -> ApkInstaller.pick(this));
        root.addView(upd, CaseViews.lp(this, 8, 0));

        LinearLayout log = new LinearLayout(this);
        log.setOrientation(LinearLayout.VERTICAL);
        log.setGravity(Gravity.CENTER_VERTICAL);
        log.setPadding(CaseViews.dp(this, 16), CaseViews.dp(this, 14),
                CaseViews.dp(this, 16), CaseViews.dp(this, 14));
        log.setBackground(CaseViews.box(this, AppTheme.card(this), 12, AppTheme.cardStroke(this), 1));
        log.addView(CaseViews.text(this, "Change log", 16, AppTheme.text(this), true));
        log.addView(CaseViews.text(this, "What every update adds. Latest: " + ChangeLog.ENTRIES[0].title + ".",
                14, AppTheme.sub(this), false));
        log.setOnClickListener(v -> startActivity(new Intent(this, ChangeLogActivity.class)));
        root.addView(log, CaseViews.lp(this, 8, 0));

        TextView bk = CaseViews.text(this, "Backup", 14, AppTheme.accent(this), true);
        bk.setPadding(0, CaseViews.dp(this, 24), 0, CaseViews.dp(this, 8));
        root.addView(bk);

        LinearLayout folder = new LinearLayout(this);
        folder.setOrientation(LinearLayout.VERTICAL);
        folder.setGravity(Gravity.CENTER_VERTICAL);
        folder.setPadding(CaseViews.dp(this, 16), CaseViews.dp(this, 14),
                CaseViews.dp(this, 16), CaseViews.dp(this, 14));
        folder.setBackground(CaseViews.box(this, AppTheme.card(this), 12, AppTheme.cardStroke(this), 1));
        folder.addView(CaseViews.text(this, "Open data folder", 16, AppTheme.text(this), true));
        folder.addView(CaseViews.text(this, "Browse the app's private data folder: documents, photos, cards and data files.",
                14, AppTheme.sub(this), false));
        folder.setOnClickListener(v -> startActivity(new Intent(this, FileExplorerActivity.class)));
        root.addView(folder, CaseViews.lp(this, 0, 8));

        LinearLayout drive = new LinearLayout(this);
        drive.setOrientation(LinearLayout.VERTICAL);
        drive.setGravity(Gravity.CENTER_VERTICAL);
        drive.setPadding(CaseViews.dp(this, 16), CaseViews.dp(this, 14),
                CaseViews.dp(this, 16), CaseViews.dp(this, 14));
        drive.setBackground(CaseViews.box(this, AppTheme.card(this), 12, AppTheme.cardStroke(this), 1));
        drive.addView(CaseViews.text(this, "Save data as zip", 16, AppTheme.text(this), true));
        driveSub = CaseViews.text(this, DriveBackup.lastLabel(this) + ". Saves cases, clients, documents and photos"
                + " as one zip; choose a folder or Google Drive in the next screen.", 14, AppTheme.sub(this), false);
        drive.addView(driveSub);
        drive.setOnClickListener(v -> DriveBackup.start(this));
        root.addView(drive, CaseViews.lp(this, 0, 0));

        LinearLayout auto = new LinearLayout(this);
        auto.setOrientation(LinearLayout.VERTICAL);
        auto.setGravity(Gravity.CENTER_VERTICAL);
        auto.setPadding(CaseViews.dp(this, 16), CaseViews.dp(this, 14),
                CaseViews.dp(this, 16), CaseViews.dp(this, 14));
        auto.setBackground(CaseViews.box(this, AppTheme.card(this), 12, AppTheme.cardStroke(this), 1));
        auto.addView(CaseViews.text(this, "Auto backup folder", 16, AppTheme.text(this), true));
        autoSub = CaseViews.text(this, DriveBackup.autoLabel(this), 14, AppTheme.sub(this), false);
        auto.addView(autoSub);
        auto.setOnClickListener(v -> {
            if (DriveBackup.autoOn(this)) {
                new android.app.AlertDialog.Builder(this).setTitle("Auto backup folder")
                        .setItems(new String[]{"Choose another folder", "Turn off"}, (d, w) -> {
                            if (w == 0) DriveBackup.chooseAutoFolder(this);
                            else {
                                DriveBackup.clearAutoFolder(this);
                                autoSub.setText(DriveBackup.autoLabel(this));
                            }
                        }).show();
            } else {
                DriveBackup.chooseAutoFolder(this);
            }
        });
        root.addView(auto, CaseViews.lp(this, 0, 0));

        LinearLayout rest = new LinearLayout(this);
        rest.setOrientation(LinearLayout.VERTICAL);
        rest.setGravity(Gravity.CENTER_VERTICAL);
        rest.setPadding(CaseViews.dp(this, 16), CaseViews.dp(this, 14),
                CaseViews.dp(this, 16), CaseViews.dp(this, 14));
        rest.setBackground(CaseViews.box(this, AppTheme.card(this), 12, AppTheme.cardStroke(this), 1));
        rest.addView(CaseViews.text(this, "Restore from backup zip", 16, AppTheme.text(this), true));
        rest.addView(CaseViews.text(this, "Use after reinstalling the app or on a new phone.",
                14, AppTheme.sub(this), false));
        rest.setOnClickListener(v -> DriveBackup.startRestore(this));
        root.addView(rest, CaseViews.lp(this, 0, 0));

        LinearLayout rem = new LinearLayout(this);
        rem.setOrientation(LinearLayout.VERTICAL);
        rem.setGravity(Gravity.CENTER_VERTICAL);
        rem.setPadding(CaseViews.dp(this, 16), CaseViews.dp(this, 14),
                CaseViews.dp(this, 16), CaseViews.dp(this, 14));
        rem.setBackground(CaseViews.box(this, AppTheme.card(this), 12, AppTheme.cardStroke(this), 1));
        rem.addView(CaseViews.text(this, "Backup reminder", 16, AppTheme.text(this), true));
        remindSub = CaseViews.text(this, DriveBackup.remindLabel(this), 14, AppTheme.sub(this), false);
        rem.addView(remindSub);
        rem.setOnClickListener(v -> PickerDialogs.optionSheet(this, "Backup reminder",
                "A notification asks you to upload your data", java.util.Arrays.asList(DriveBackup.DAY_LABELS),
                indexOfDays(DriveBackup.remindDays(this)), which -> {
                    DriveBackup.setRemindDays(this, DriveBackup.DAYS[which]);
                    remindSub.setText(DriveBackup.remindLabel(this));
                    if (DriveBackup.DAYS[which] > 0) DriveBackup.askNotificationPermission(this);
                }));
        root.addView(rem, CaseViews.lp(this, 0, 0));

        sv.addView(root);
        setContentView(sv);
        // opened from the reminder notification: go straight to the backup
        if (getIntent().getBooleanExtra(DriveBackup.EXTRA_START, false)) {
            getIntent().removeExtra(DriveBackup.EXTRA_START);
            sv.post(() -> DriveBackup.start(this));
        }
    }

    private TextView driveSub, remindSub, autoSub;

    private static int indexOfDays(int d) {
        for (int i = 0; i < DriveBackup.DAYS.length; i++) if (DriveBackup.DAYS[i] == d) return i;
        return 3;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (driveSub != null) {
            driveSub.setText(DriveBackup.lastLabel(this) + ". Saves cases, clients, documents and photos"
                    + " as one zip; choose a folder or Google Drive in the next screen.");
        }
        if (autoSub != null) autoSub.setText(DriveBackup.autoLabel(this));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(DriveBackup.EXTRA_START, false)) {
            intent.removeExtra(DriveBackup.EXTRA_START);
            DriveBackup.start(this);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == DriveBackup.REQ_SAVE && res == RESULT_OK && data != null) {
            DriveBackup.handle(this, data.getData());
            return;
        }
        if (req == DriveBackup.REQ_RESTORE && res == RESULT_OK && data != null) {
            DriveBackup.handleRestore(this, data.getData());
            return;
        }
        if (req == DriveBackup.REQ_TREE && res == RESULT_OK && data != null) {
            DriveBackup.handleTree(this, data.getData());
            if (autoSub != null) autoSub.setText(DriveBackup.autoLabel(this));
            return;
        }
        if (req == ApkInstaller.REQ_PICK && res == RESULT_OK && data != null) {
            ApkInstaller.handle(this, data.getData());
        }
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
