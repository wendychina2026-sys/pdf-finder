package com.example.twobuttons;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

class PermissionHelper {
    private final Activity activity;

    PermissionHelper(Activity activity) {
        this.activity = activity;
    }

    boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        return activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    // Android 11+: opens settings screen. Older: shows permission popup.
    void request() {
        if (Build.VERSION.SDK_INT >= 30) {
            Toast.makeText(activity, "Turn on \"Allow access to all files\", then go back",
                    Toast.LENGTH_LONG).show();
            Uri uri = Uri.parse("package:" + activity.getPackageName());
            try {
                activity.startActivity(new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri));
            } catch (Exception e) {
                activity.startActivity(
                        new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            activity.requestPermissions(
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
    }
}
