package com.example.twobuttons;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Backs up the whole PdfFinder data folder as one zip to Google Drive (or any place the user picks in the
 * system file picker, Drive included) and reminds the user from time to time to do it.
 */
final class DriveBackup {
    static final int REQ_SAVE = 7301;
    static final int REQ_NOTIF = 7302;
    static final int REQ_RESTORE = 7304;
    static final int REQ_TREE = 7305;
    static final String EXTRA_START = "start_backup";
    static final String ACTION_REMIND = "com.example.twobuttons.BACKUP_REMIND";

    /** Reminder every N days; 0 = off. */
    static final int[] DAYS = {0, 1, 3, 7, 14, 30};
    static final String[] DAY_LABELS = {"Off", "Every day", "Every 3 days", "Every week", "Every 2 weeks",
            "Every month"};
    private static final int DEFAULT_DAYS = 7;

    private static final String PREFS = "app_runtime";      // device specific, never part of the backup itself
    private static final String K_LAST = "drive_last_backup";
    private static final String K_DAYS = "drive_remind_days";
    private static final String K_SINCE = "drive_remind_since";
    private static final String K_ASKED = "drive_notif_asked";
    private static final String K_TREE = "auto_backup_tree";
    private static final String K_AUTO = "auto_backup_last";
    private static final String K_SLOT = "auto_backup_slot";
    private static final String K_WARNED = "stale_warned_day";
    private static final long AUTO_EVERY_MS = 3L * 60 * 60 * 1000;
    private static final long STALE_MS = 3L * 24 * 60 * 60 * 1000;
    private static final java.util.concurrent.atomic.AtomicBoolean AUTO_RUNNING =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final String CHANNEL = "backup_reminder";
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private DriveBackup() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ backup

    /** Opens the system "save as" screen; the user picks Google Drive (or any folder) there. */
    static void start(Activity a) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_TITLE, "PdfFinder_backup_"
                + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".zip");
        try {
            a.startActivityForResult(i, REQ_SAVE);
        } catch (Exception e) {
            Toast.makeText(a, "No file picker found on this phone", Toast.LENGTH_LONG).show();
        }
    }

    /** Result of start(): zips the folder straight into the chosen place (Drive uploads it by itself). */
    static void handle(final Activity a, final Uri uri) {
        if (uri == null) return;
        Toast.makeText(a, "Backing up your data...", Toast.LENGTH_SHORT).show();
        final Context app = a.getApplicationContext();
        new Thread(() -> {
            String msg;
            try {
                OutputStream os = app.getContentResolver().openOutputStream(uri, "w");
                if (os == null) throw new IllegalStateException("Cannot write to the chosen place");
                DataFolder.zipTo(app, os);
                sp(app).edit().putLong(K_LAST, System.currentTimeMillis()).apply();
                msg = "Backup saved. If you chose Google Drive it uploads in the background.";
            } catch (Exception e) {
                msg = "Backup failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            final String shown = msg;
            a.runOnUiThread(() -> Toast.makeText(a, shown, Toast.LENGTH_LONG).show());
        }).start();
    }

    /** "Last backup: 25 Oct 2026" or "Never backed up". */
    static String lastLabel(Context c) {
        long t = sp(c).getLong(K_LAST, 0);
        return t == 0 ? "Never backed up"
                : "Last backup: " + new SimpleDateFormat("dd MMM yyyy", Locale.US).format(new Date(t));
    }

    // ------------------------------------------------------------------ restore from zip

    static void startRestore(Activity a) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed",
                "application/octet-stream"});
        try {
            a.startActivityForResult(i, REQ_RESTORE);
        } catch (Exception e) {
            Toast.makeText(a, "No file picker found on this phone", Toast.LENGTH_LONG).show();
        }
    }

    /** Asks first, then restores the chosen zip into the private folder. */
    static void handleRestore(final Activity a, final Uri uri) {
        if (uri == null) return;
        new android.app.AlertDialog.Builder(a)
                .setTitle("Restore from backup zip")
                .setMessage("Data in the zip is added to this app. Same entries are replaced by the zip.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Restore", (d, w) -> {
                    Toast.makeText(a, "Restoring...", Toast.LENGTH_SHORT).show();
                    final Context app = a.getApplicationContext();
                    new Thread(() -> {
                        String msg;
                        boolean ok = false;
                        try (java.io.InputStream in = app.getContentResolver().openInputStream(uri)) {
                            if (in == null) throw new IllegalStateException("Cannot read the chosen file");
                            int n = DataFolder.restoreZip(app, in);
                            ok = n > 0;
                            msg = ok ? "Restored " + n + " files" : "This zip is not a PdfFinder backup";
                        } catch (Exception e) {
                            msg = "Restore failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                        }
                        final String shown = msg;
                        final boolean done = ok;
                        a.runOnUiThread(() -> {
                            Toast.makeText(a, shown, Toast.LENGTH_LONG).show();
                            if (done) a.recreate();
                        });
                    }).start();
                })
                .show();
    }

    // ------------------------------------------------------------------ automatic backup to a chosen folder

    static void chooseAutoFolder(Activity a) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            a.startActivityForResult(i, REQ_TREE);
        } catch (Exception e) {
            Toast.makeText(a, "No file picker found on this phone", Toast.LENGTH_LONG).show();
        }
    }

    static void handleTree(Activity a, Uri tree) {
        if (tree == null) return;
        try {
            a.getContentResolver().takePersistableUriPermission(tree,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception e) {
            Toast.makeText(a, "Cannot keep access to this folder", Toast.LENGTH_LONG).show();
            return;
        }
        sp(a).edit().putString(K_TREE, tree.toString()).putLong(K_AUTO, 0).apply();
        Toast.makeText(a, "Auto backup folder set. First backup running...", Toast.LENGTH_LONG).show();
        autoIfDue(a.getApplicationContext(), true);
    }

    static void clearAutoFolder(Context c) {
        sp(c).edit().remove(K_TREE).remove(K_AUTO).apply();
    }

    static boolean autoOn(Context c) {
        return sp(c).getString(K_TREE, null) != null;
    }

    static String autoLabel(Context c) {
        String t = sp(c).getString(K_TREE, null);
        if (t == null) return "Off. Choose a folder once; the app then saves a fresh zip by itself.";
        String id;
        try { id = android.provider.DocumentsContract.getTreeDocumentId(Uri.parse(t)); } catch (Exception e) { id = t; }
        long last = sp(c).getLong(K_AUTO, 0);
        return "On: " + id.replace("primary:", "Internal storage/") + (last == 0 ? ". Waiting for first backup."
                : ". Last auto backup " + new SimpleDateFormat("dd MMM, HH:mm", Locale.US).format(new Date(last)) + ".");
    }

    /** Writes the zip into the chosen folder (two files in turn, so one is always complete). */
    static void autoIfDue(final Context c, boolean force) {
        final Context app = c.getApplicationContext();
        final String t = sp(app).getString(K_TREE, null);
        if (t == null) return;
        if (!force && System.currentTimeMillis() - sp(app).getLong(K_AUTO, 0) < AUTO_EVERY_MS) return;
        if (!AUTO_RUNNING.compareAndSet(false, true)) return;
        new Thread(() -> {
            try {
                Uri tree = Uri.parse(t);
                android.content.ContentResolver cr = app.getContentResolver();
                Uri parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,
                        android.provider.DocumentsContract.getTreeDocumentId(tree));
                int slot = sp(app).getInt(K_SLOT, 0) == 0 ? 1 : 0;
                String name = "PdfFinder_auto_backup_" + (slot + 1) + ".zip";
                Uri target = findChild(cr, tree, name);
                if (target == null) {
                    target = android.provider.DocumentsContract.createDocument(cr, parent, "application/zip", name);
                }
                if (target == null) throw new IllegalStateException("cannot create file");
                OutputStream os = cr.openOutputStream(target, "wt");
                if (os == null) throw new IllegalStateException("cannot write");
                DataFolder.zipTo(app, os);
                long now = System.currentTimeMillis();
                sp(app).edit().putInt(K_SLOT, slot).putLong(K_AUTO, now).putLong(K_LAST, now).apply();
            } catch (Exception ignored) {
                // folder removed or storage full: the stale warning tells the user
            } finally {
                AUTO_RUNNING.set(false);
            }
        }).start();
    }

    private static Uri findChild(android.content.ContentResolver cr, Uri tree, String name) {
        Uri kids = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree,
                android.provider.DocumentsContract.getTreeDocumentId(tree));
        try (android.database.Cursor cur = cr.query(kids, new String[]{
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            while (cur != null && cur.moveToNext()) {
                if (name.equals(cur.getString(1))) {
                    return android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, cur.getString(0));
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    // ------------------------------------------------------------------ stale backup warning

    static boolean isStale(Context c) {
        long last = sp(c).getLong(K_LAST, 0);
        return System.currentTimeMillis() - last > STALE_MS;
    }

    /** Main page: once a day, a message when there is data but no recent backup. */
    static void warnIfStale(Activity a) {
        if (!(a instanceof MainActivity)) return;
        Context c = a.getApplicationContext();
        if (!isStale(c)) return;
        long day = System.currentTimeMillis() / DAY_MS;
        if (sp(c).getLong(K_WARNED, 0) == day) return;
        sp(c).edit().putLong(K_WARNED, day).apply();
        long last = sp(c).getLong(K_LAST, 0);
        String msg = last == 0 ? "No backup yet. Data is private; uninstall deletes it. Settings > Backup."
                : "Last backup " + Math.max(1, (System.currentTimeMillis() - last) / DAY_MS)
                + " days ago. Settings > Backup.";
        Toast.makeText(a, msg, Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ reminder

    static int remindDays(Context c) {
        return sp(c).getInt(K_DAYS, DEFAULT_DAYS);
    }

    static String remindLabel(Context c) {
        int d = remindDays(c);
        for (int i = 0; i < DAYS.length; i++) if (DAYS[i] == d) return DAY_LABELS[i];
        return "Every " + d + " days";
    }

    static void setRemindDays(Context c, int days) {
        sp(c).edit().putInt(K_DAYS, days).putLong(K_SINCE, System.currentTimeMillis()).apply();
        schedule(c);
    }

    /** One inexact daily alarm (around 10:00); the receiver decides whether a reminder is due. */
    static void schedule(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            PendingIntent pi = alarmIntent(c);
            if (remindDays(c) <= 0) {
                am.cancel(pi);
                return;
            }
            SharedPreferences p = sp(c);
            if (p.getLong(K_SINCE, 0) == 0) p.edit().putLong(K_SINCE, System.currentTimeMillis()).apply();
            Calendar at = Calendar.getInstance();
            at.set(Calendar.HOUR_OF_DAY, 10);
            at.set(Calendar.MINUTE, 0);
            at.set(Calendar.SECOND, 0);
            if (at.getTimeInMillis() <= System.currentTimeMillis()) at.add(Calendar.DAY_OF_YEAR, 1);
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, at.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
        } catch (Exception ignored) {
            // a missing reminder must never break the app
        }
    }

    private static PendingIntent alarmIntent(Context c) {
        Intent i = new Intent(c, BackupReminderReceiver.class).setAction(ACTION_REMIND);
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Called by the daily alarm: posts the notification when the last backup is old enough. */
    static void remindIfDue(Context c) {
        int days = remindDays(c);
        if (days <= 0) return;
        SharedPreferences p = sp(c);
        long base = Math.max(p.getLong(K_LAST, 0), p.getLong(K_SINCE, 0));
        long now = System.currentTimeMillis();
        if (now - base < days * DAY_MS - 3 * 60 * 60 * 1000L) return;     // 3 h slack: the alarm is inexact
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Backup reminder",
                    NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent open = new Intent(c, SettingsActivity.class).putExtra(EXTRA_START, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(c, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long last = p.getLong(K_LAST, 0);
        String text = last == 0
                ? "Your data has not been backed up yet. Tap to upload it to Google Drive."
                : "Last backup was " + Math.max(1, (now - last) / DAY_MS)
                + " days ago. Tap to upload your data to Google Drive.";
        nm.notify(7303, new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_upload)
                .setContentTitle("Back up your PdfFinder data")
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build());
    }

    /** Android 13+: ask once for the right to show the reminder. */
    static void askNotificationPermission(Activity a) {
        if (Build.VERSION.SDK_INT < 33) return;
        if (a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        SharedPreferences p = sp(a);
        if (p.getBoolean(K_ASKED, false)) return;
        p.edit().putBoolean(K_ASKED, true).apply();
        a.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
    }
}
