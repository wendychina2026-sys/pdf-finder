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
 * The app's own PRIVATE folder: <app files dir>/PdfFinder. No file manager and no other app can see it;
 * only this app can. Data is kept safe by zip backups (Settings > Backup), not by a public folder.
 *
 *   PdfFinder/Data       one JSON file per store (cases, clients, diary, options ...), kept up to date
 *   PdfFinder/Documents  PDFs received through Share / Open with
 *   PdfFinder/Photos     camera photos taken inside the app
 *   PdfFinder/Cards      visiting-card pictures of opposing advocates
 *
 * Uninstalling the app deletes this folder. Restore comes from a zip backup (Settings > Backup).
 * Folders of older versions (public PdfFinder, Android/data, Documents/PDFFinder) are moved in once.
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
    private static final String K_MIGRATED = "private_migrated_v1";
    private static final String K_DIRTY = "private_migrate_dirty";
    private static Context APP;

    private DataFolder() {}

    // ------------------------------------------------------------------ folder access

    static boolean canWrite() { return APP != null; }

    /** The private data folder (files dir / PdfFinder), created on demand. */
    static File root() {
        if (APP == null) return null;
        File r = new File(APP.getFilesDir(), ROOT_NAME);
        if (!r.isDirectory() && !r.mkdirs()) return null;
        noMedia(r);
        return r;
    }

    /** Puts an empty .nomedia file in a folder so galleries / WhatsApp never list its images. */
    static void noMedia(File d) {
        try {
            if (d == null) return;
            if (!d.isDirectory() && !d.mkdirs()) return;
            File n = new File(d, ".nomedia");
            if (!n.exists()) n.createNewFile();
        } catch (Exception ignored) { }
    }

    /** .nomedia in the app's external folders (Android/data/.../files, case_photos, shared). */
    static void hideExternal(Context c) {
        try {
            File ext = c.getExternalFilesDir(null);
            if (ext == null) return;
            noMedia(ext);
            File p = new File(ext, "case_photos");
            if (p.isDirectory()) noMedia(p);
            File sh = new File(ext, "shared");
            if (sh.isDirectory()) noMedia(sh);
        } catch (Exception ignored) { }
    }

    /** A sub folder of the data folder; fallback is used when the folder is not available. */
    static File dir(String sub, File fallback) {
        try {
            File r = root();
            if (r != null) {
                File d = new File(r, sub);
                if (d.isDirectory() || d.mkdirs()) { noMedia(d); return d; }
            }
        } catch (Exception ignored) { }
        if (fallback != null) { fallback.mkdirs(); noMedia(fallback); }
        return fallback;
    }

    /** The Documents folder of the data folder (private fallback while "all files" access is off). */
    static File documentsDir(Context c) {
        return dir("Documents", new File(c.getExternalFilesDir(null), "shared"));
    }

    /** FileProvider uri string -> file, for every file under the Documents folder (sub folders too). */
    static Map<String, File> documentsByUri(Context c) {
        Map<String, File> out = new java.util.HashMap<>();
        java.util.ArrayDeque<File> st = new java.util.ArrayDeque<>();
        File root = documentsDir(c);
        if (root != null) st.push(root);
        while (!st.isEmpty()) {
            File[] ch = st.pop().listFiles();
            if (ch == null) continue;
            for (File f : ch) {
                if (f.getName().startsWith(".")) continue;
                if (f.isDirectory()) {
                    st.push(f);
                } else {
                    try {
                        out.put(PhotoFlow.uri(c, f).toString(), f);
                    } catch (Exception ignored) { }
                }
            }
        }
        return out;
    }

    /**
     * Copies a PDF from another app / the file picker into dir. The same document already in dir is
     * reused (no second copy). Null when the file cannot be read. Run on the main thread only for small files.
     */
    static File importPdf(Context c, android.net.Uri uri, File dir) {
        File tmp = null;
        try {
            if (!dir.exists()) dir.mkdirs();
            String name = uriName(c, uri);
            if (name == null || name.isEmpty()) name = "shared_" + System.currentTimeMillis();
            name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (!name.toLowerCase().endsWith(".pdf")) name += ".pdf";
            tmp = new File(dir, ".incoming_" + System.currentTimeMillis());
            try (java.io.InputStream is = c.getContentResolver().openInputStream(uri);
                 java.io.OutputStream os = new FileOutputStream(tmp)) {
                if (is == null) { tmp.delete(); return null; }
                byte[] buf = new byte[16384];
                int r;
                while ((r = is.read(buf)) > 0) os.write(buf, 0, r);
            }
            File same = findSameFile(dir, tmp);
            if (same != null) { tmp.delete(); return same; }
            File out = new File(dir, name);
            int n = 1;
            while (out.exists()) {      // same name but a different document: keep both, numbered
                out = new File(dir, name.replaceFirst("(?i)\\.pdf$", "") + "_" + n++ + ".pdf");
            }
            if (!tmp.renameTo(out)) { tmp.delete(); return null; }
            return out;
        } catch (Exception e) {
            if (tmp != null) tmp.delete();
            return null;
        }
    }

    /**
     * Copies any picked file (PDF, Word, image, photo) into dir, keeping its extension. The same document
     * already in dir is reused (no second copy). Null when the file cannot be read.
     */
    static File importFile(Context c, android.net.Uri uri, File dir) {
        File tmp = null;
        try {
            if (!dir.exists()) dir.mkdirs();
            String name = uriName(c, uri);
            if (name == null || name.isEmpty()) name = "file_" + System.currentTimeMillis();
            name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (name.lastIndexOf('.') <= 0) {
                String mt = c.getContentResolver().getType(uri);
                String ext = mt == null ? null : android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mt);
                if (ext != null && !ext.isEmpty()) name += "." + ext;
            }
            tmp = new File(dir, ".incoming_" + System.currentTimeMillis());
            try (java.io.InputStream is = c.getContentResolver().openInputStream(uri);
                 java.io.OutputStream os = new FileOutputStream(tmp)) {
                if (is == null) { tmp.delete(); return null; }
                byte[] buf = new byte[16384];
                int r;
                while ((r = is.read(buf)) > 0) os.write(buf, 0, r);
            }
            File same = findSameFile(dir, tmp);
            if (same != null) { tmp.delete(); return same; }
            int dot = name.lastIndexOf('.');
            String stem = dot > 0 ? name.substring(0, dot) : name;
            String ext2 = dot > 0 ? name.substring(dot) : "";
            File out = new File(dir, name);
            for (int n = 1; out.exists(); n++) out = new File(dir, stem + "_" + n + ext2);
            if (!tmp.renameTo(out)) { tmp.delete(); return null; }
            return out;
        } catch (Exception e) {
            if (tmp != null) tmp.delete();
            return null;
        }
    }

    private static String uriName(Context c, android.net.Uri uri) {
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cur = c.getContentResolver().query(uri,
                    new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cur != null && cur.moveToFirst()) return cur.getString(0);
            } catch (Exception ignored) { }
        }
        return uri.getLastPathSegment();
    }

    private static String sha256(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) md.update(buf, 0, r);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * A file already in {@code dir} that is the same document as {@code src} (same size and same content;
     * the one with the same name is tried first), or null. Used so one PDF never exists twice in the folder.
     */
    static File findSameFile(File dir, File src) {
        try {
            File[] all = dir.listFiles();
            if (all == null || src == null) return null;
            String h = null;
            File sameName = new File(dir, src.getName());
            List<File> order = new ArrayList<>();
            if (sameName.isFile()) order.add(sameName);
            for (File f : all) if (f.isFile() && !f.equals(sameName)) order.add(f);
            for (File f : order) {
                if (f.equals(src) || f.getName().startsWith(".") || f.length() != src.length()) continue;
                if (h == null) { h = sha256(src); if (h == null) return null; }
                if (h.equals(sha256(f))) return f;
            }
        } catch (Exception ignored) { }
        return null;
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
            File same = findSameFile(dir, src);
            if (same != null) {                              // already in the folder: keep that one only
                recordOrigin(c, same, src.getAbsolutePath(), false);
                src.delete();
                android.media.MediaScannerConnection.scanFile(c, new String[]{src.getAbsolutePath()}, null, null);
                return same;
            }
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
            recordOrigin(c, dest, src.getAbsolutePath(), true);
            android.media.MediaScannerConnection.scanFile(c, new String[]{src.getAbsolutePath(), dest.getAbsolutePath()}, null, null);
            return dest;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The real file behind a picked / shared uri when it can be reached by path (all files access):
     * the file is then used in place and moved (not copied) when saved. Null when only a stream is available.
     */
    static File localOriginal(Context c, android.net.Uri uri) {
        try {
            if (uri == null) return null;
            if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) return null;
            File f = null;
            if ("file".equals(uri.getScheme())) f = new File(uri.getPath());
            else if ("content".equals(uri.getScheme())) f = resolvePath(c, uri);
            if (f == null || !f.isFile() || !f.canRead() || f.length() <= 0) return null;
            String cp = f.getCanonicalPath();
            if (cp.startsWith(c.getApplicationInfo().dataDir)) return null;
            File ext = c.getExternalFilesDir(null);
            if (ext != null && ext.getParentFile() != null && cp.startsWith(ext.getParentFile().getCanonicalPath())) return null;
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Takes a picked file into the Documents folder right now. Reachable file: really moved (one copy only).
     * Stream only: copied, and its original removed later by releaseFor() when possible.
     */
    static File takeIn(Context c, android.net.Uri uri) {
        File src = localOriginal(c, uri);
        if (src != null) {
            File moved = moveIntoDocuments(c, src);
            if (moved != null) return moved;
        }
        File dir = documentsDir(c);
        File cp = dir == null ? null : importFile(c, uri, dir);
        if (cp != null) registerOriginal(c, uri, cp);
        return cp;
    }

    private static final String PENDING = "pending_originals";
    private static final String ORIGINS = "origin_locations";

    /** Remembers where a stored file came from (its original folder / path), for the file information dialog. */
    static void recordOrigin(Context c, File stored, String origin, boolean overwrite) {
        try {
            if (stored == null || origin == null || origin.isEmpty()) return;
            SharedPreferences sp = c.getSharedPreferences(ORIGINS, Context.MODE_PRIVATE);
            String k = stored.getAbsolutePath();
            if (!overwrite && sp.contains(k)) return;
            sp.edit().putString(k, origin).apply();
        } catch (Exception ignored) { }
    }

    /** Where the file was before it was moved into the app, or "" when that was not recorded. */
    static String originOf(Context c, File stored) {
        try {
            return c.getSharedPreferences(ORIGINS, Context.MODE_PRIVATE).getString(stored.getAbsolutePath(), "");
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * A file was copied into the app folder from somewhere else. Its original is NOT touched yet: it is
     * remembered here and removed only when the user saves the file (Save to Vault / attach to matter),
     * see releaseFor(). The real path is resolved now, while the picker / share grant is still valid.
     */
    static void registerOriginal(Context c, android.net.Uri uri, File stored) {
        try {
            if (uri == null || stored == null) return;
            String auth = uri.getAuthority();
            if (auth != null && auth.equals(c.getPackageName() + ".fileprovider")) return;   // already ours
            File path = null;
            if ("file".equals(uri.getScheme())) path = new File(uri.getPath());
            else if ("content".equals(uri.getScheme())) path = resolvePath(c, uri);
            c.getSharedPreferences(PENDING, Context.MODE_PRIVATE).edit()
                    .putString(stored.getAbsolutePath(), (path == null ? "" : path.getAbsolutePath()) + "\n" + uri)
                    .apply();
            recordOrigin(c, stored, path != null ? path.getAbsolutePath()
                    : "Shared from " + (auth == null ? "another app" : auth), true);
        } catch (Exception ignored) { }
    }

    /** The user saved these documents (their app-folder uris): remove the originals they were copied from. */
    static void releaseFor(Context c, java.util.Collection<String> savedUris) {
        try {
            SharedPreferences sp = c.getSharedPreferences(PENDING, Context.MODE_PRIVATE);
            Map<String, ?> all = sp.getAll();
            if (all.isEmpty() || savedUris == null) return;
            SharedPreferences.Editor ed = sp.edit();
            for (Map.Entry<String, ?> e : all.entrySet()) {
                File stored = new File(e.getKey());
                if (!savedUris.contains(PhotoFlow.uri(c, stored).toString())) continue;
                String v = String.valueOf(e.getValue());
                int nl = v.indexOf('\n');
                String pth = nl < 0 ? "" : v.substring(0, nl);
                String uri = nl < 0 ? v : v.substring(nl + 1);
                removeOriginal(c, pth.isEmpty() ? null : new File(pth), android.net.Uri.parse(uri), stored);
                ed.remove(e.getKey());
            }
            ed.apply();
        } catch (Exception ignored) { }
    }

    /** Deletes the original (never the stored copy, never anything inside the app's own folders). */
    private static boolean removeOriginal(Context c, File path, android.net.Uri uri, File stored) {
        try {
            if (stored == null || !stored.isFile() || stored.length() <= 0) return false;
            if (path != null) {
                String cp = path.getCanonicalPath();
                if (cp.equals(stored.getCanonicalPath())) return false;
                if (cp.startsWith(c.getApplicationInfo().dataDir)) return false;
                File ext = c.getExternalFilesDir(null);
                if (ext != null && ext.getParentFile() != null && cp.startsWith(ext.getParentFile().getCanonicalPath())) return false;
            }
            boolean gone = false;
            if (path != null && path.isFile()) gone = path.delete();
            if (!gone && uri != null && "content".equals(uri.getScheme())) {
                try {
                    if (android.provider.DocumentsContract.isDocumentUri(c, uri))
                        gone = android.provider.DocumentsContract.deleteDocument(c.getContentResolver(), uri);
                } catch (Exception ignored) { }
                if (!gone) {
                    try { gone = c.getContentResolver().delete(uri, null, null) > 0; } catch (Exception ignored) { }
                }
            }
            if (gone && path != null) {
                android.media.MediaScannerConnection.scanFile(c, new String[]{path.getAbsolutePath()}, null, null);
            }
            return gone;
        } catch (Exception e) {
            return false;
        }
    }

    /** Best guess at the real file behind a picked content uri; null when it cannot be told. */
    /**
     * A shared uri that gives no path: the file usually still sits in shared storage (WhatsApp keeps received
     * media in Android/media/com.whatsapp/..., reachable with all files access). Same name and same size = same file.
     */
    private static File findByNameSize(Context c, android.net.Uri uri) {
        try {
            String name = null;
            long size = -1;
            try (android.database.Cursor cur = c.getContentResolver().query(uri, null, null, null, null)) {
                if (cur != null && cur.moveToFirst()) {
                    int ni = cur.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    int si = cur.getColumnIndex(android.provider.OpenableColumns.SIZE);
                    if (ni >= 0) name = cur.getString(ni);
                    if (si >= 0 && !cur.isNull(si)) size = cur.getLong(si);
                }
            }
            if (name == null || name.isEmpty() || size <= 0) return null;
            File sd = Environment.getExternalStorageDirectory();
            String[] roots = {
                    "Android/media/com.whatsapp/WhatsApp/Media", "Android/media/com.whatsapp.w4b/WhatsApp Business/Media",
                    "WhatsApp/Media", "Download", "Documents", "DCIM", "Pictures"};
            for (String r : roots) {
                File hit = searchName(new File(sd, r), name, size, 4);
                if (hit != null) return hit;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private static File searchName(File dir, String name, long size, int depth) {
        File[] kids = dir.listFiles();
        if (kids == null) return null;
        for (File f : kids) {
            if (f.isFile() && f.getName().equals(name) && f.length() == size) return f;
        }
        if (depth > 0) {
            for (File f : kids) {
                if (f.isDirectory() && !f.getName().startsWith(".")) {
                    File hit = searchName(f, name, size, depth - 1);
                    if (hit != null) return hit;
                }
            }
        }
        return null;
    }

    static File resolvePath(Context c, android.net.Uri uri) {
        try {
            if (android.provider.DocumentsContract.isDocumentUri(c, uri)) {
                String id = android.provider.DocumentsContract.getDocumentId(uri);
                String auth = uri.getAuthority();
                if ("com.android.externalstorage.documents".equals(auth) && id.contains(":")) {
                    String vol = id.substring(0, id.indexOf(':'));
                    String rel = id.substring(id.indexOf(':') + 1);
                    File base = "primary".equalsIgnoreCase(vol)
                            ? Environment.getExternalStorageDirectory() : new File("/storage/" + vol);
                    return new File(base, rel);
                }
                if (id.startsWith("raw:")) return new File(id.substring(4));
            }
            try (android.database.Cursor cur = c.getContentResolver().query(uri,
                    new String[]{android.provider.MediaStore.MediaColumns.DATA}, null, null, null)) {
                if (cur != null && cur.moveToFirst() && cur.getString(0) != null) return new File(cur.getString(0));
            } catch (Exception ignored) { }
            if (android.provider.DocumentsContract.isDocumentUri(c, uri)) {
                String id = android.provider.DocumentsContract.getDocumentId(uri);
                int k = id.lastIndexOf(':');
                if (k > 0 && uri.getAuthority() != null && uri.getAuthority().equals("com.android.providers.media.documents")) {
                    String type = id.substring(0, k);
                    android.net.Uri tbl = "image".equals(type) ? android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            : "video".equals(type) ? android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            : android.provider.MediaStore.Files.getContentUri("external");
                    try (android.database.Cursor cur = c.getContentResolver().query(tbl,
                            new String[]{android.provider.MediaStore.MediaColumns.DATA},
                            "_id=?", new String[]{id.substring(k + 1)}, null)) {
                        if (cur != null && cur.moveToFirst() && cur.getString(0) != null) return new File(cur.getString(0));
                    }
                }
            }
            // other app's provider with no path (WhatsApp share, ...): find the same file by name + size
            File byName = findByNameSize(c, uri);
            if (byName != null) return byName;
        } catch (Exception ignored) { }
        return null;
    }

    // ------------------------------------------------------------------ start up

    static void install(final Application app) {
        APP = app.getApplicationContext();
        hideExternal(APP);
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity a) { onResume(app, a); }
            @Override public void onActivityPaused(Activity a) { backupAsync(app); DriveBackup.autoIfDue(app, false); }
            @Override public void onActivityCreated(Activity a, Bundle b) { }
            @Override public void onActivityStarted(Activity a) { }
            @Override public void onActivityStopped(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
            @Override public void onActivityDestroyed(Activity a) { }
        });
    }

    /**
     * Files received while "All files access" was off were kept in the app's own folder
     * (Android/data/com.example.twobuttons/files/shared). Once the access is on they move into
     * PdfFinder/Documents; their page marks, matter and document records follow them.
     */
    static void migrateStranded(final Context c) {
        IO.execute(new Runnable() {
            @Override public void run() {
                try {
                    File root = root();
                    File ext = c.getExternalFilesDir(null);
                    if (root == null || ext == null) return;
                    File[] old = new File(ext, "shared").listFiles();
                    if (old == null || old.length == 0) return;
                    LegalStore ls = new LegalStore(c);
                    for (File f : old) {
                        if (!f.isFile()) continue;
                        String oldUri = PhotoFlow.uri(c, f).toString();
                        File moved = moveIntoDocuments(c, f);
                        if (moved == null || moved.equals(f)) continue;
                        new MarkStore(c).move(c, f.getAbsolutePath(), moved.getAbsolutePath());
                        String newUri = PhotoFlow.uri(c, moved).toString();
                        for (LegalStore.Doc d : ls.docs()) {
                            boolean changed = false;
                            if (oldUri.equals(d.uri)) { d.uri = newUri; changed = true; }
                            for (int k = 0; k < d.more.size(); k++) {
                                if (oldUri.equals(d.more.get(k))) { d.more.set(k, newUri); changed = true; }
                            }
                            if (changed) ls.saveDoc(d);
                        }
                    }
                } catch (Exception ignored) { }
            }
        });
    }

    /**
     * A PDF saved to the General Document Vault with no page marked never showed in the Vault. Once per file:
     * every page gets the document's own category, so the file appears in the Vault with its pages marked.
     */
    static void markUnmarkedVaultPdfs(final Context c) {
        IO.execute(new Runnable() {
            @Override public void run() {
                try {
                    File[] files = documentsDir(c).listFiles();
                    if (files == null) return;
                    SharedPreferences done = c.getSharedPreferences("vault_repaired", Context.MODE_PRIVATE);
                    MarkStore ms = new MarkStore(c);
                    java.util.Map<String, String> catByUri = new java.util.HashMap<>();
                    for (LegalStore.Doc d : new LegalStore(c).docs()) {
                        if (d.category == null || d.category.isEmpty() || d.category.equals("Multiple")) continue;
                        catByUri.put(d.uri, d.category);
                        for (String m : d.more) catByUri.put(m, d.category);
                    }
                    for (File f : files) {
                        if (!f.isFile() || !f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) continue;
                        String path = f.getAbsolutePath();
                        if (done.getBoolean(path, false) || !ms.all(path).isEmpty()) continue;
                        String cat = catByUri.get(PhotoFlow.uri(c, f).toString());
                        if (cat == null) continue;
                        try (android.os.ParcelFileDescriptor pf = android.os.ParcelFileDescriptor.open(f,
                                android.os.ParcelFileDescriptor.MODE_READ_ONLY);
                             android.graphics.pdf.PdfRenderer pr = new android.graphics.pdf.PdfRenderer(pf)) {
                            for (int pg = 0; pg < pr.getPageCount(); pg++) ms.set(path, pg, cat);
                            done.edit().putBoolean(path, true).apply();
                        } catch (Exception ignored) { }
                    }
                } catch (Exception ignored) { }
            }
        });
    }

    /**
     * Documents saved to the General Document Vault from the file picker kept only the picker's own
     * link and never reached the app folder. Once per record: copy the file in, point the record at the
     * copy and, for a single image, mark it with the record's category so the Vault lists it.
     */
    static void rescueOutsideDocs(final Context c) {
        IO.execute(new Runnable() {
            @Override public void run() {
                try {
                    File dir = documentsDir(c);
                    if (dir == null) return;
                    SharedPreferences tried = c.getSharedPreferences("rescue_tried", Context.MODE_PRIVATE);
                    LegalStore ls = new LegalStore(c);
                    MarkStore ms = new MarkStore(c);
                    for (LegalStore.Doc d : ls.docs()) {
                        if (d.uri == null || d.uri.isEmpty() || !d.more.isEmpty() || tried.getBoolean(d.id, false)) continue;
                        android.net.Uri u = android.net.Uri.parse(d.uri);
                        if (!"content".equals(u.getScheme())
                                || (c.getPackageName() + ".fileprovider").equals(u.getAuthority())) continue;
                        tried.edit().putBoolean(d.id, true).apply();
                        File f = importFile(c, u, dir);
                        if (f == null) continue;
                        d.uri = PhotoFlow.uri(c, f).toString();
                        ls.saveDoc(d);
                        boolean general = d.caseName == null || d.caseName.isEmpty()
                                || d.caseName.equals("General Document Vault");
                        if (general && (DocFileCard.kind(f.getName()) == 1 || DocFileCard.kind(f.getName()) == 2)
                                && ms.all(f.getAbsolutePath()).isEmpty()
                                && d.category != null && !d.category.isEmpty() && !d.category.equals("Multiple")) {
                            ms.set(f.getAbsolutePath(), 0, d.category);
                        }
                    }
                } catch (Exception ignored) { }
            }
        });
    }

    private static void onResume(Application app, final Activity a) {
        migrateToPrivate(app, a);
        repairDocUris(app);
        migrateStranded(app);
        markUnmarkedVaultPdfs(app);
        rescueOutsideDocs(app);
        DriveBackup.warnIfStale(a);
        try {
            SharedPreferences rt = app.getSharedPreferences(RUNTIME, Context.MODE_PRIVATE);
            if (rt.getBoolean(FLAG, false)) return;
            File root = root();
            if (root == null) return;
            File data = new File(root, "Data");
            boolean restored = false;
            if (isEmpty(app) && data.isDirectory()) restored = restore(app, data);
            rt.edit().putBoolean(FLAG, true).apply();
            if (restored) {
                Toast.makeText(app, "Data restored", Toast.LENGTH_LONG).show();
                a.recreate();
            }
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

    // ------------------------------------------------------------------ move into the private folder

    /**
     * Once: moves everything older versions kept in public places (PdfFinder on internal storage,
     * Android/data/.../files, Documents/PDFFinder, files/adv_cards) into the private folder, then rewrites
     * every stored path / link so marks, cases and documents still point at their files.
     * Needs "All files access" only to read the old public folder; runs again at each start until done.
     */
    static void migrateToPrivate(final Context c, final Activity a) {
        IO.execute(new Runnable() {
            @Override public void run() {
                try {
                    if (doMigrate(c)) {
                        a.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                Toast.makeText(c, "Your data folder is now private to this app", Toast.LENGTH_LONG).show();
                                a.recreate();
                            }
                        });
                    }
                } catch (Exception ignored) { }
            }
        });
    }

    private static boolean canReadPublic(Context c) {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return c.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private static String canon(File f) {
        try { return f.getCanonicalPath(); } catch (Exception e) { return f.getAbsolutePath(); }
    }

    /** from, to for plain paths, to for FileProvider links (canonical). */
    private static void addMap(List<String[]> out, File src, File dst) {
        String toAbs = dst.getAbsolutePath(), toCan = canon(dst);
        Set<String> froms = new LinkedHashSet<>();
        froms.add(src.getAbsolutePath());
        froms.add(canon(src));
        for (String f : froms) out.add(new String[]{f, toAbs, toCan});
    }

    private static List<String[]> legacyMaps(Context c, File newRoot) {
        List<String[]> m = new ArrayList<>();
        File pubRoot = new File(Environment.getExternalStorageDirectory(), ROOT_NAME);
        addMap(m, pubRoot, newRoot);
        addMap(m, new File("/storage/emulated/0/" + ROOT_NAME), newRoot);
        File ext = c.getExternalFilesDir(null);
        if (ext != null) {
            addMap(m, new File(ext, "shared"), new File(newRoot, "Documents"));
            addMap(m, new File(ext, "case_photos"), new File(newRoot, "Photos"));
        }
        addMap(m, new File(c.getFilesDir(), "adv_cards"), new File(newRoot, "Cards"));
        addMap(m, new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "PDFFinder"),
                new File(newRoot, "Extracted"));
        return m;
    }

    private static boolean doMigrate(Context c) {
        SharedPreferences rt = c.getSharedPreferences(RUNTIME, Context.MODE_PRIVATE);
        if (rt.getBoolean(K_MIGRATED, false)) return false;
        File newRoot = root();
        if (newRoot == null) return false;
        boolean pubOk = canReadPublic(c);
        File pubRoot = new File(Environment.getExternalStorageDirectory(), ROOT_NAME);
        File pubDocs = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "PDFFinder");
        File ext = c.getExternalFilesDir(null);
        boolean changed = false;

        // fresh install over an old public backup: bring the data back first
        File pubData = new File(pubRoot, "Data");
        if (pubOk && pubData.isDirectory() && isEmpty(c) && restore(c, pubData)) {
            changed = true;
            rt.edit().putBoolean(K_DIRTY, true).apply();
        }

        List<String[]> exact = new ArrayList<>();
        int[] fails = {0};
        int[] moved = {0};
        if (pubOk && pubRoot.isDirectory()) moveTree(pubRoot, newRoot, true, exact, fails, moved);
        if (pubOk && pubDocs.isDirectory()) moveTree(pubDocs, new File(newRoot, "Extracted"), false, exact, fails, moved);
        if (ext != null) {
            moveTree(new File(ext, "shared"), new File(newRoot, "Documents"), false, exact, fails, moved);
            moveTree(new File(ext, "case_photos"), new File(newRoot, "Photos"), false, exact, fails, moved);
        }
        moveTree(new File(c.getFilesDir(), "adv_cards"), new File(newRoot, "Cards"), false, exact, fails, moved);
        if (moved[0] > 0) {
            changed = true;
            rt.edit().putBoolean(K_DIRTY, true).apply();
        }
        if (fails[0] > 0) return changed;                      // try the rest at the next start

        if (rt.getBoolean(K_DIRTY, false) || !exact.isEmpty()) {
            List<String[]> maps = new ArrayList<>(exact);
            maps.addAll(legacyMaps(c, newRoot));
            rewriteStores(c, maps);
            rt.edit().putBoolean(K_DIRTY, false).apply();
            changed = true;
        }
        if (pubOk) {
            deleteTree(pubData);
            pubRoot.delete();
            pubDocs.delete();
            rt.edit().putBoolean(K_MIGRATED, true).apply();
        }
        return changed;
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    /** Moves src into dst (merging). skipData: leave the top-level Data folder (rebuilt by the backup). */
    private static void moveTree(File src, File dst, boolean skipData, List<String[]> exact, int[] fails, int[] moved) {
        if (!src.isDirectory()) return;
        File[] kids = src.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.isDirectory()) {
                if (skipData && f.getName().equals("Data")) continue;
                moveTree(f, new File(dst, f.getName()), false, exact, fails, moved);
            } else if (f.getName().startsWith(".incoming_") || f.getName().endsWith(".tmp")) {
                f.delete();
            } else {
                moveOne(f, dst, exact, fails, moved);
            }
        }
        src.delete();       // only succeeds when empty
    }

    private static void moveOne(File f, File dstDir, List<String[]> exact, int[] fails, int[] moved) {
        try {
            if (!dstDir.isDirectory() && !dstDir.mkdirs()) { fails[0]++; return; }
            File target = new File(dstDir, f.getName());
            if (target.exists()) {
                if (target.length() == f.length()) {
                    String h1 = sha256(f), h2 = sha256(target);
                    if (h1 != null && h1.equals(h2)) { f.delete(); return; }
                }
                String name = f.getName();
                int dot = name.lastIndexOf('.');
                String stem = dot > 0 ? name.substring(0, dot) : name;
                String ext = dot > 0 ? name.substring(dot) : "";
                for (int n = 1; target.exists(); n++) target = new File(dstDir, stem + "_" + n + ext);
                String[] m = {f.getAbsolutePath(), target.getAbsolutePath(), canon(target)};
                exact.add(m);
                exact.add(new String[]{android.net.Uri.encode(f.getAbsolutePath(), "/"),
                        android.net.Uri.encode(target.getAbsolutePath(), "/"),
                        android.net.Uri.encode(canon(target), "/")});
            }
            File tmp = new File(dstDir, ".mig_" + target.getName());
            try (FileInputStream in = new FileInputStream(f); FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                out.getFD().sync();
            }
            if (tmp.length() != f.length() || !tmp.renameTo(target)) { tmp.delete(); fails[0]++; return; }
            target.setLastModified(f.lastModified());
            f.delete();
            moved[0]++;
        } catch (Exception e) {
            fails[0]++;
        }
    }

    private static String rewrite(String s, List<String[]> maps) {
        if (s == null || s.isEmpty()) return s;
        for (String[] m : maps) {
            if (!s.contains(m[0])) continue;
            s = s.replace("/root" + m[0], "/root" + m[2]);      // FileProvider links
            s = s.replace(m[0], m[1]);                            // plain paths
        }
        return s;
    }

    /** Rewrites old paths / links in every stored key and value. True when anything changed. */
    private static boolean rewriteStores(Context c, List<String[]> maps) {
        boolean any = false;
        for (String name : storeNames(c)) {
            SharedPreferences sp = c.getSharedPreferences(name, Context.MODE_PRIVATE);
            Map<String, ?> all = sp.getAll();
            SharedPreferences.Editor ed = sp.edit();
            boolean ch = false;
            for (Map.Entry<String, ?> e : all.entrySet()) {
                String k = e.getKey(), nk = rewrite(k, maps);
                Object v = e.getValue(), nv = v;
                if (v instanceof String) {
                    nv = rewrite((String) v, maps);
                } else if (v instanceof Set) {
                    Set<String> ns = new LinkedHashSet<>();
                    boolean sc = false;
                    for (Object o : (Set<?>) v) {
                        String so = String.valueOf(o), sn = rewrite(so, maps);
                        if (!so.equals(sn)) sc = true;
                        ns.add(sn);
                    }
                    if (sc) nv = ns;
                }
                if (nk.equals(k) && nv.equals(v)) continue;
                ch = true;
                if (!nk.equals(k)) ed.remove(k);
                if (nv instanceof String) ed.putString(nk, (String) nv);
                else if (nv instanceof Boolean) ed.putBoolean(nk, (Boolean) nv);
                else if (nv instanceof Integer) ed.putInt(nk, (Integer) nv);
                else if (nv instanceof Long) ed.putLong(nk, (Long) nv);
                else if (nv instanceof Float) ed.putFloat(nk, (Float) nv);
                else if (nv instanceof Set) {
                    Set<String> ss = new LinkedHashSet<>();
                    for (Object o : (Set<?>) nv) ss.add(String.valueOf(o));
                    ed.putStringSet(nk, ss);
                }
            }
            if (ch) { ed.commit(); any = true; }
        }
        return any;
    }

    // ------------------------------------------------------------------ keep document links in step with the files

    /**
     * Document records find their file by the exact FileProvider link. When a stored link is not the
     * current form of an existing file (or the file only moved), the link is re-made from the file, so the
     * record keeps its own name, category and stage.
     */
    static void repairDocUris(final Context c) {
        IO.execute(new Runnable() {
            @Override public void run() {
                try {
                    LegalStore ls = new LegalStore(c);
                    for (LegalStore.Doc d : ls.docs()) {
                        boolean ch = false;
                        String u = fixUri(c, d.uri);
                        if (!u.equals(d.uri)) { d.uri = u; ch = true; }
                        for (int k = 0; k < d.more.size(); k++) {
                            String m = fixUri(c, d.more.get(k));
                            if (!m.equals(d.more.get(k))) { d.more.set(k, m); ch = true; }
                        }
                        if (ch) ls.saveDoc(d);
                    }
                } catch (Exception ignored) { }
            }
        });
    }

    private static String fixUri(Context c, String u) {
        try {
            if (u == null || u.isEmpty()) return u;
            android.net.Uri p = android.net.Uri.parse(u);
            if (!(c.getPackageName() + ".fileprovider").equals(p.getAuthority())) return u;
            String path = p.getPath();
            if (path == null || !path.startsWith("/root/")) return u;
            File f = new File(path.substring("/root".length()));
            if (!f.isFile()) {
                File found = findByName(documentsDir(c), f.getName());
                if (found == null) return u;
                f = found;
            }
            return PhotoFlow.uri(c, f).toString();
        } catch (Exception e) {
            return u;
        }
    }

    /** The one file with this name under dir, or null when none or several. */
    private static File findByName(File dir, String name) {
        if (dir == null) return null;
        List<File> hits = new ArrayList<>();
        java.util.ArrayDeque<File> st = new java.util.ArrayDeque<>();
        st.push(dir);
        while (!st.isEmpty()) {
            File[] kids = st.pop().listFiles();
            if (kids == null) continue;
            for (File k : kids) {
                if (k.isDirectory()) st.push(k);
                else if (k.getName().equals(name)) hits.add(k);
            }
        }
        return hits.size() == 1 ? hits.get(0) : null;
    }

    // ------------------------------------------------------------------ restore from a zip

    /**
     * Restores a zip made by zipTo(): files go into the private folder, stores are loaded from
     * PdfFinder/Data, old paths are rewritten. Returns the number of files restored (0 = not a backup).
     */
    static int restoreZip(Context c, java.io.InputStream in) throws Exception {
        File root = root();
        if (root == null) throw new IllegalStateException("Data folder not available");
        String rootCanon = canon(root);
        int count = 0;
        byte[] buf = new byte[16384];
        try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(new java.io.BufferedInputStream(in))) {
            java.util.zip.ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                String name = e.getName().replace('\\', '/');
                if (!name.startsWith(ROOT_NAME + "/")) continue;
                File out = new File(root, name.substring(ROOT_NAME.length() + 1));
                String oc = canon(out);
                if (!oc.equals(rootCanon) && !oc.startsWith(rootCanon + "/")) continue;   // zip-slip guard
                if (e.isDirectory()) { out.mkdirs(); continue; }
                File parent = out.getParentFile();
                if (parent != null) parent.mkdirs();
                File tmp = new File(parent, ".rst_" + out.getName());
                try (FileOutputStream os = new FileOutputStream(tmp)) {
                    int n;
                    while ((n = z.read(buf)) > 0) os.write(buf, 0, n);
                }
                if (!tmp.renameTo(out)) { out.delete(); tmp.renameTo(out); }
                count++;
            }
        }
        if (count == 0) return 0;
        File data = new File(root, "Data");
        if (data.isDirectory()) restore(c, data);
        rewriteStores(c, legacyMaps(c, root));
        return count;
    }
}
