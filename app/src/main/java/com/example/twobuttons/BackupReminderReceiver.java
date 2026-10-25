package com.example.twobuttons;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Daily alarm -> backup reminder when due; after a reboot the alarm is set again. */
public class BackupReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i == null ? null : i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a)) {
            DriveBackup.schedule(c);
        } else if (DriveBackup.ACTION_REMIND.equals(a)) {
            DriveBackup.remindIfDue(c);
        }
    }
}
