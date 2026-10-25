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
