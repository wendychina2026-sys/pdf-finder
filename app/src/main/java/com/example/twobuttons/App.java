package com.example.twobuttons;

import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

import java.io.File;

/** Clears cached temp files once whenever a new version of the app has been installed. */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            SharedPreferences sp = getSharedPreferences("app_runtime", MODE_PRIVATE);
            long last = sp.getLong("last_update_time", -1);
            if (last != pi.lastUpdateTime) {
                if (last != -1) {   // not the very first launch after a fresh install
                    wipe(getCacheDir());
                    wipe(getExternalCacheDir());
                }
                sp.edit().putLong("last_update_time", pi.lastUpdateTime).apply();
            }
        } catch (Exception ignored) {
            // cache cleanup is best-effort
        }
    }

    /** Deletes everything inside dir (the folder itself stays). Saved cases/files are not touched. */
    private static void wipe(File dir) {
        if (dir == null) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.isDirectory()) wipe(f);
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }
}
