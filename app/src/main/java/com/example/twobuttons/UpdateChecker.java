package com.example.twobuttons;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.pm.PackageInfo;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Settings > Check for update. Asks GitHub for the latest release of the repo; the release tag ends
 * with the build number (v1.0.N, N = versionCode). Newer than installed: download the .apk asset
 * and open the system installer (one tap to confirm). Needs a public repo.
 */
final class UpdateChecker {

    private static final String LATEST =
            "https://api.github.com/repos/wendychina2026-sys/pdf-finder/releases/latest";

    private UpdateChecker() { }

    static void check(Activity a) {
        Toast.makeText(a, "Checking for update...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String err = null, tag = null, url = null, notes = "";
            long build = -1;
            try {
                HttpURLConnection c = open(LATEST);
                int code = c.getResponseCode();
                if (code == 404) {
                    err = "No release found. Repo is private, or no release published yet.";
                } else if (code != 200) {
                    err = "GitHub answered HTTP " + code + ". Try again later.";
                } else {
                    JSONObject j = new JSONObject(read(c.getInputStream()));
                    tag = j.optString("tag_name", "");
                    notes = j.optString("name", tag);
                    Matcher m = Pattern.compile("(\\d+)$").matcher(tag);
                    if (m.find()) build = Long.parseLong(m.group(1));
                    JSONArray as = j.optJSONArray("assets");
                    for (int i = 0; as != null && i < as.length(); i++) {
                        JSONObject o = as.getJSONObject(i);
                        if (o.optString("name").endsWith(".apk")) {
                            url = o.optString("browser_download_url");
                            break;
                        }
                    }
                    if (build < 0 || url == null) err = "Latest release has no usable APK or build number.";
                }
            } catch (Exception e) {
                err = "Cannot reach GitHub. Check internet.\n" + e.getMessage();
            }
            final String fErr = err, fTag = tag, fUrl = url;
            final long fBuild = build;
            a.runOnUiThread(() -> result(a, fErr, fTag, fBuild, fUrl));
        }).start();
    }

    private static void result(Activity a, String err, String tag, long build, String url) {
        if (a.isFinishing()) return;
        if (err != null) {
            info(a, "Update check failed", err);
            return;
        }
        PackageInfo cur = ApkInstaller.installed(a);
        long have = cur == null ? 0 : ApkInstaller.code(cur);
        if (build <= have) {
            info(a, "Up to date", "Installed: build " + have + "\nLatest on GitHub: build " + build);
            return;
        }
        new AlertDialog.Builder(a).setTitle("Update available")
                .setMessage("Installed: build " + have + "\nNew: " + tag + " (build " + build
                        + ")\n\nDownload and install?")
                .setNegativeButton("Later", null)
                .setPositiveButton("Install", (d, w) -> download(a, url))
                .show();
    }

    private static void download(Activity a, String url) {
        ProgressDialog pd = new ProgressDialog(a);
        pd.setMessage("Downloading update...");
        pd.setCancelable(false);
        pd.show();
        new Thread(() -> {
            File out = null;
            String err = null;
            try {
                HttpURLConnection c = open(url);   // follows redirect to the asset host
                if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
                File dir = new File(a.getCacheDir(), "apk");
                dir.mkdirs();
                out = new File(dir, "update.apk");
                try (InputStream in = c.getInputStream(); FileOutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                }
            } catch (Exception e) {
                err = "Download failed: " + e.getMessage();
                out = null;
            }
            final File f = out;
            final String fErr = err;
            a.runOnUiThread(() -> {
                pd.dismiss();
                if (a.isFinishing()) return;
                if (f == null) { info(a, "Update failed", fErr); return; }
                PackageInfo apk = a.getPackageManager().getPackageArchiveInfo(f.getAbsolutePath(), 0);
                if (apk == null || !a.getPackageName().equals(apk.packageName)) {
                    info(a, "Update failed", "Downloaded file is not this app's APK.");
                    return;
                }
                ApkInstaller.install(a, f);
            });
        }).start();
    }

    private static HttpURLConnection open(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "pdf-finder-updater");
        c.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream");
        return c;
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toString("UTF-8");
    }

    private static void info(Activity a, String title, String msg) {
        new AlertDialog.Builder(a).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show();
    }
}
