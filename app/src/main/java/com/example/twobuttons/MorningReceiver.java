package com.example.twobuttons;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 8 AM alarm -> greeting. Reboot / app update clear alarms, so they set it again. */
public class MorningReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i == null ? null : i.getAction();
        if (MorningGreeting.ACTION.equals(a)) {
            MorningGreeting.fire(c);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            MorningGreeting.schedule(c);
        }
    }
}
