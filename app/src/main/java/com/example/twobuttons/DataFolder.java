package com.example.twobuttons;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The app's own folder on the phone's internal storage: /storage/emulated/0/PdfFinder
 *
 *   PdfFinder/Data       one JSON file per store (cases, clients, diary, options ...), kept up to date
 *   PdfFinder/Documents  PDFs received through Share / Open with
 *   PdfFinder/Photos     camera photos taken inside the app
 *   PdfFinder/Cards      visiting-card pictures of opposing advocates
 *
 * Because the folder lives outside the app's private storage it survives uninstall / reinstall.
 * When the app starts with no data of its own and the folder holds a backup, the data is restored.
 */
final class DataFolder {
    static final String ROOT_NAME = "PdfFinder";
    private static final String RUNTIME = "app_runtime";
    private static final String FLAG = "data_folder_ready";
    /** Stores that are never backed up (temporary / device specific). */
    private static final Set<String> SKIP = new LinkedHashSet<>();
    static {
        SKIP.add(RUNTIME);
        SKIP.add("ecourts_dir");   // big court directory, downloaded again on demand
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private DataFolder() {}

    // ------------------------------------------------------------------ folder access

    static boolean canWrite() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /** /storage/emulated/0/PdfFinder, or null while the "all files" access is not granted. */
    static File root() {
        if (!canWrite()) return null;
        File r = new File(Environment.getExternalStorageDirectory(), ROOT_NAME);
        if (!r.isDirectory() && !r.mkdirs()) return null;
        return r;
    }

    /** A sub folder of the data folder; fallback is used when the folder is not available. */
    static File dir(String sub, File fallback) {
        try {
            File r = root();
            if (r != null) {
                File d = new File(r, sub);
                if (d.isDirectory() || d.mkdirs()) return d;
            }
        } catch (Exception ignored) { }
        if (fallback != null) fallback.mkdirs();
        return fallback;
    }

    /**
     * Moves a saved file from its original place into the app's Documents folder (PdfFinder/Documents).
     * Returns the new file, the same file when it already lives there, or null when it could not be moved
     * (the original is then left untouched).
     */
    static File moveIntoDocuments(Context c, File src) {
        try {
            if (src == null || !src.isFile()) return null;
            File dir = dir("Documents", new File(c.getExternalFilesDir(null), "shared"));
            if (dir == null) return null;
            if (src.getParentFile() != null
                    && src.getParentFile().getCanonicalPath().equals(dir.getCanonicalPath())) return src;
            String name = src.getName();
            int dot = name.lastIndexOf('.');
            String stem = dot > 0 ? name.substring(0, dot) : name;
            String ext = dot > 0 ? name.substring(dot) : "";
            File dest = new File(dir, name);
            for (int n = 1; dest.exists(); n++) dest = new File(dir, stem + " (" + n + ")" + ext);
            if (!src.renameTo(dest)) {                       // different volume: copy, then delete the original
                try (FileInputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(dest)) {
                    byte[] buf = new byte[64 * 1024];
                    int r;
                    while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                }
                if (dest.length() != src.length()) { dest.delete(); return null; }
                if (!src.delete()) { /* copy is complete; the original could not be removed */ }
            }
            android.media.MediaScannerConnection.scanFile(c, new String[]{src.getAbsolutePath(), dest.getAbsolutePath()}, null, null);
            return dest;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ start up

    static void install(final Application app) {
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity a) { onResume(app, a); }
            @Override public void onActivityPaused(Activity a) { backupAsync(app); }
            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override public void onActivityStarted(Activity a) { }
            @Override public void onActivityStopped(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { }
        });
    }

    private static void onResume(Application app, Activity a) {
        try {
            SharedPreferences rt = app.getSharedPreferences(RUNTIME, Context.MODE_PRIVATE);
            if (rt.getBoolean(FLAG, false)) return;
            File root = root();
            if (root == null) return;                       // wait until access is granted
            File data = new File(root, "Data");
            boolean restored = false;
            if (isEmpty(app) && data.isDirectory()) restored = restore(app, data);
            rt.edit().putBoolean(FLAG, true).apply();
            Toast.makeText(app, restored
                    ? "Data restored from " + root.getAbsolutePath()
                    : "Your data is saved in " + root.getAbsolutePath(), Toast.LENGTH_LONG).show();
            if (restored) a.recreate();
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ backup

    private static void backupAsync(final Application app) {
        IO.execute(new Runnable() {
            @Override public void run() { backup(app); }
        });
    }

    private static void backup(Application app) {
        try {
            if (!app.getSharedPreferences(RUNTIME, Context.MODE_PRIVATE).getBoolean(FLAG, false)) return;
            File root = root();
            if (root == null) return;
            File data = new File(root, "Data");
            if (!data.isDirectory() && !data.mkdirs()) return;
            for (String name : storeNames(app)) {
                SharedPreferences sp = app.getSharedPreferences(name, Context.MODE_PRIVATE);
                Map<String, ?> all = sp.getAll();
                File out = new File(data, name + ".json");
                if (all.isEmpty()) continue;                // never replace a backup with nothing
                String json = toJson(name, all).toString(1);
                if (out.exists() && json.equals(read(out))) continue;
                File tmp = new File(data, name + ".json.tmp");
                try (FileOutputStream os = new FileOutputStream(tmp)) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                    os.getFD().sync();
                }
                if (!tmp.renameTo(out)) { out.delete(); tmp.renameTo(out); }
            }
        } catch (Exception ignored) { }
    }

    private static List<String> storeNames(Context c) {
        List<String> names = new ArrayList<>();
        File dir = new File(c.getApplicationInfo().dataDir, "shared_prefs");
        File[] fs = dir.listFiles();
        if (fs == null) return names;
        for (File f : fs) {
            String n = f.getName();
            if (!n.endsWith(".xml")) continue;
            n = n.substring(0, n.length() - 4);
            if (!SKIP.contains(n)) names.add(n);
        }
        return names;
    }

    private static JSONObject toJson(String name, Map<String, ?> all) throws Exception {
        JSONObject values = new JSONObject();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            Object v = e.getValue();
            JSONObject o = new JSONObject();
            if (v instanceof String) { o.put("t", "s"); o.put("v", v); }
            else if (v instanceof Boolean) { o.put("t", "b"); o.put("v", v); }
            else if (v instanceof Integer) { o.put("t", "i"); o.put("v", v); }
            else if (v instanceof Long) { o.put("t", "l"); o.put("v", v); }
            else if (v instanceof Float) { o.put("t", "f"); o.put("v", (double) (Float) v); }
            else if (v instanceof Set) {
                JSONArray arr = new JSONArray();
                for (Object s : (Set<?>) v) arr.put(String.valueOf(s));
                o.put("t", "ss"); o.put("v", arr);
            } else continue;
            values.put(e.getKey(), o);
        }
        JSONObject root = new JSONObject();
        root.put("store", name);
        root.put("values", values);
        return root;
    }

    // ------------------------------------------------------------------ zip for Google Drive

    /**
     * Writes the whole app data as one zip: PdfFinder/Data (every store, taken fresh from the app),
     * PdfFinder/Documents, PdfFinder/Photos, PdfFinder/Cards and anything else in the PdfFinder folder.
     * Files kept in the app's own storage (when the folder was not available) are included under the
     * same names. Closes os.
     */
    static void zipTo(Context c, java.io.OutputStream os) throws Exception {
        final Set<String> done = new LinkedHashSet<>();
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(
                new java.io.BufferedOutputStream(os))) {
            for (String name : storeNames(c)) {
                Map<String, ?> all = c.getSharedPreferences(name, Context.MODE_PRIVATE).getAll();
                if (all.isEmpty()) continue;
                String entry = ROOT_NAME + "/Data/" + name + ".json";
                z.putNextEntry(new java.util.zip.ZipEntry(entry));
                z.write(toJson(name, all).toString(1).getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
                done.add(entry);
            }
            File root = root();
            if (root != null) addDir(z, root, ROOT_NAME, done);
            File ext = c.getExternalFilesDir(null);
            if (ext != null) {
                addDir(z, new File(ext, "case_photos"), ROOT_NAME + "/Photos", done);
                addDir(z, new File(ext, "shared"), ROOT_NAME + "/Documents", done);
            }
            addDir(z, new File(c.getFilesDir(), "adv_cards"), ROOT_NAME + "/Cards", done);
        }
    }

    private static void addDir(java.util.zip.ZipOutputStream z, File dir, String prefix, Set<String> done)
            throws Exception {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        byte[] buf = new byte[16384];
        for (File f : fs) {
            String entry = prefix + "/" + f.getName();
            if (f.isDirectory()) {
                addDir(z, f, entry, done);
            } else if (!f.getName().endsWith(".tmp") && done.add(entry)) {
                z.putNextEntry(new java.util.zip.ZipEntry(entry));
                try (FileInputStream in = new FileInputStream(f)) {
                    int n;
                    while ((n = in.read(buf)) > 0) z.write(buf, 0, n);
                }
                z.closeEntry();
            }
        }
    }

    // ------------------------------------------------------------------ restore

    /** True when the app holds no data of its own (fresh install). */
    private static boolean isEmpty(Context c) {
        for (String n : storeNames(c)) {
            if (!c.getSharedPreferences(n, Context.MODE_PRIVATE).getAll().isEmpty()) return false;
        }
        return true;
    }

    private static boolean restore(Context c, File dataDir) {
        boolean any = false;
        File[] fs = dataDir.listFiles();
        if (fs == null) return false;
        for (File f : fs) {
            if (!f.getName().endsWith(".json")) continue;
            try {
                JSONObject root = new JSONObject(read(f));
                String name = root.getString("store");
                if (SKIP.contains(name)) continue;
                JSONObject values = root.getJSONObject("values");
                SharedPreferences.Editor ed = c.getSharedPreferences(name, Context.MODE_PRIVATE).edit();
                Iterator<String> it = values.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject o = values.getJSONObject(k);
                    switch (o.getString("t")) {
                        case "s": ed.putString(k, o.getString("v")); break;
                        case "b": ed.putBoolean(k, o.getBoolean("v")); break;
                        case "i": ed.putInt(k, o.getInt("v")); break;
                        case "l": ed.putLong(k, o.getLong("v")); break;
                        case "f": ed.putFloat(k, (float) o.getDouble("v")); break;
                        case "ss":
                            Set<String> set = new LinkedHashSet<>();
                            JSONArray arr = o.getJSONArray("v");
                            for (int i = 0; i < arr.length(); i++) set.add(arr.getString(i));
                            ed.putStringSet(k, set);
                            break;
                        default: break;
                    }
                }
                ed.commit();
                any = true;
            } catch (Exception ignored) { }
        }
        return any;
    }

    private static String read(File f) throws Exception {
        try (FileInputStream is = new FileInputStream(f)) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
