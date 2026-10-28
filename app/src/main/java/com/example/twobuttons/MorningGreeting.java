package com.example.twobuttons;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import java.util.Calendar;

/**
 * Every morning at 8:00 AM (phone local time) shows the notification
 * "Hello Shaifali , Good Morning , Have a Great Day Ahead !!!!".
 * One alarm at a time: each time it fires (or after reboot / app update / app start) the next 8:00 is set.
 */
final class MorningGreeting {

    static final String ACTION = "com.example.twobuttons.MORNING_GREETING";
    static final int HOUR = 8;
    static final String TEXT = "Hello Shaifali , Good Morning , Have a Great Day Ahead !!!!";

    private static final String CHANNEL = "morning_greeting";
    private static final int NOTIF_ID = 8001;

    private MorningGreeting() { }

    /** Sets the alarm for the next 8:00 AM. Safe to call any number of times. */
    static void schedule(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Calendar at = Calendar.getInstance();
            at.set(Calendar.HOUR_OF_DAY, HOUR);
            at.set(Calendar.MINUTE, 0);
            at.set(Calendar.SECOND, 0);
            at.set(Calendar.MILLISECOND, 0);
            if (at.getTimeInMillis() <= System.currentTimeMillis() + 1000) at.add(Calendar.DAY_OF_YEAR, 1);
            PendingIntent pi = alarmIntent(c);
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.getTimeInMillis(), pi);
            } else {   // exact-alarm right not granted: still fires, a few minutes late at worst
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.getTimeInMillis(), pi);
            }
        } catch (Exception ignored) {
            // a missing greeting must never break the app
        }
    }

    private static PendingIntent alarmIntent(Context c) {
        Intent i = new Intent(c, MorningReceiver.class).setAction(ACTION);
        return PendingIntent.getBroadcast(c, 8, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Posts the greeting, then sets tomorrow's alarm. */
    static void fire(Context c) {
        schedule(c);   // first: tomorrow's alarm must be set even if the notification fails
        try {
            if (Build.VERSION.SDK_INT >= 33
                    && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) return;
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Morning greeting",
                        NotificationManager.IMPORTANCE_DEFAULT));
            }
            Intent open = new Intent(c, SplashActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent tap = PendingIntent.getActivity(c, 8, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            nm.notify(NOTIF_ID, new NotificationCompat.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_morning)
                    .setContentTitle("Good Morning")
                    .setContentText(TEXT)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(TEXT))
                    .setContentIntent(tap)
                    .setAutoCancel(true)
                    .build());
        } catch (Exception ignored) {
            // best-effort
        }
    }
}
