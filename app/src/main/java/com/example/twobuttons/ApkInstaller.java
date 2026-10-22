package com.example.twobuttons;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Install an update from an APK file on the phone. Before the system installer opens, compares the
 * APK's version with the installed one and tells the user: same version, older version (Android
 * refuses downgrades) or newer (update). Works offline.
 */
final class ApkInstaller {

    static final int REQ_PICK = 4101;

    private ApkInstaller() { }

    static void pick(Activity a) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/vnd.android.package-archive",
                "application/octet-stream"});
        try {
            a.startActivityForResult(i, REQ_PICK);
        } catch (Exception e) {
            Toast.makeText(a, "No file picker available on this device", Toast.LENGTH_LONG).show();
        }
    }

    static long code(PackageInfo p) {
        return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
    }

    static PackageInfo installed(Activity a) {
        try {
            return a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
        } catch (Exception e) {
            return null;
        }
    }

    /** "1.0.5 (build 5)" of the running app. */
    static String installedLabel(Activity a) {
        PackageInfo p = installed(a);
        return p == null ? "unknown" : label(p);
    }

    private static String label(PackageInfo p) {
        return p.versionName + " (build " + code(p) + ")";
    }

    /** Call from onActivityResult for REQ_PICK. */
    static void handle(Activity a, Uri uri) {
        if (uri == null) return;
        new Thread(() -> {
            File out = null;
            try {
                File dir = new File(a.getCacheDir(), "apk");
                dir.mkdirs();
                out = new File(dir, "update.apk");
                try (InputStream in = a.getContentResolver().openInputStream(uri);
                     FileOutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while (in != null && (n = in.read(buf)) > 0) os.write(buf, 0, n);
                }
            } catch (Exception e) {
                out = null;
            }
            final File f = out;
            a.runOnUiThread(() -> compare(a, f));
        }).start();
    }

    private static void compare(Activity a, File f) {
        PackageInfo cur = installed(a);
        PackageInfo apk = f == null ? null
                : a.getPackageManager().getPackageArchiveInfo(f.getAbsolutePath(), 0);
        if (cur == null || apk == null) {
            Toast.makeText(a, "Not a valid APK file", Toast.LENGTH_LONG).show();
            return;
        }
        if (!a.getPackageName().equals(apk.packageName)) {
            info(a, "Different app", "This APK is for another app (" + apk.packageName
                    + "), not this one. Pick this app's APK.");
            return;
        }
        long n = code(apk), c = code(cur);
        String versions = "Installed: " + label(cur) + "\nSelected: " + label(apk);
        if (n == c) {
            new AlertDialog.Builder(a).setTitle("Same version already installed")
                    .setMessage(versions + "\n\nNothing new to install.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Reinstall anyway", (d, w) -> install(a, f))
                    .show();
        } else if (n < c) {
            info(a, "Older version", versions + "\n\nAndroid will not install an older version over a "
                    + "newer one. Pick a newer APK. (Uninstalling first would delete app data.)");
        } else {
            new AlertDialog.Builder(a).setTitle("Update available")
                    .setMessage(versions + "\n\nInstall the newer version?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Install", (d, w) -> install(a, f))
                    .show();
        }
    }

    private static void info(Activity a, String title, String msg) {
        new AlertDialog.Builder(a).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show();
    }

    private static void install(Activity a, File f) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(a).setTitle("Allow installs")
                    .setMessage("Allow this app to install apps, then pick the APK again.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Open settings", (d, w) -> a.startActivity(new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + a.getPackageName()))))
                    .show();
            return;
        }
        try {
            Uri u = FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(u, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(a, "Cannot start installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
