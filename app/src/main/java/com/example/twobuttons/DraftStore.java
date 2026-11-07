package com.example.twobuttons;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * One saved draft of the Upload Legal Document box: a private copy of every file plus the details filled
 * in so far, so an app update or restart never costs the typing again. Cleared once the upload is saved.
 */
final class DraftStore {
    static final class Draft {
        final List<File> files = new ArrayList<>();
        final List<String[]> meta = new ArrayList<>();     // per file: name, category, stage, matter, pages (null = none)
        final List<String> notes = new ArrayList<>();
        String name = "", category = "", stage = "", matter = "";
        boolean multi;
    }

    private DraftStore() { }

    private static File dir(Context c) {
        return new File(c.getFilesDir(), "upload_draft");
    }

    static boolean exists(Context c) {
        return new File(dir(c), "draft.json").isFile();
    }

    static void clear(Context c) {
        File[] fs = dir(c).listFiles();
        if (fs != null) for (File f : fs) f.delete();
        dir(c).delete();
    }

    /** Copies every file and writes the details. Run off the UI thread. Returns false when something failed. */
    static boolean save(Context c, List<Uri> uris, List<String> names, List<String[]> meta, List<String> notes,
                        String name, String category, String stage, String matter, boolean multi) {
        try {
            clear(c);
            File d = dir(c);
            if (!d.mkdirs() && !d.isDirectory()) return false;
            JSONArray arr = new JSONArray();
            for (int i = 0; i < uris.size(); i++) {
                String safe = names.get(i).replaceAll("[^A-Za-z0-9._ -]", "_");
                File out = new File(d, i + "_" + safe);
                try (InputStream in = c.getContentResolver().openInputStream(uris.get(i));
                     OutputStream os = new FileOutputStream(out)) {
                    if (in == null) return false;
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                }
                JSONObject o = new JSONObject();
                o.put("file", out.getName());
                String[] m = meta.get(i);
                if (m != null) {
                    JSONArray ma = new JSONArray();
                    for (String x : m) ma.put(x == null ? "" : x);
                    o.put("meta", ma);
                }
                String nt = notes.get(i);
                if (nt != null) o.put("note", nt);
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("files", arr);
            root.put("name", name).put("category", category).put("stage", stage)
                    .put("matter", matter).put("multi", multi);
            try (OutputStream os = new FileOutputStream(new File(d, "draft.json"))) {
                os.write(root.toString().getBytes("UTF-8"));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static Draft load(Context c) {
        try {
            File jf = new File(dir(c), "draft.json");
            if (!jf.isFile()) return null;
            byte[] b = new byte[(int) jf.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(jf)) {
                int off = 0;
                while (off < b.length) {
                    int n = in.read(b, off, b.length - off);
                    if (n < 0) break;
                    off += n;
                }
            }
            JSONObject root = new JSONObject(new String(b, "UTF-8"));
            Draft dr = new Draft();
            JSONArray arr = root.getJSONArray("files");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                File f = new File(dir(c), o.getString("file"));
                if (!f.isFile()) continue;
                dr.files.add(f);
                JSONArray ma = o.optJSONArray("meta");
                if (ma == null) dr.meta.add(null);
                else {
                    String[] m = new String[5];
                    for (int k = 0; k < 5; k++) m[k] = k < ma.length() ? ma.getString(k) : "";
                    dr.meta.add(m);
                }
                dr.notes.add(o.has("note") ? o.getString("note") : null);
            }
            if (dr.files.isEmpty()) return null;
            dr.name = root.optString("name", "");
            dr.category = root.optString("category", "");
            dr.stage = root.optString("stage", "");
            dr.matter = root.optString("matter", "");
            dr.multi = root.optBoolean("multi", false);
            return dr;
        } catch (Exception e) {
            return null;
        }
    }

    static Uri uri(Context c, File f) {
        return PhotoFlow.uri(c, f);
    }
}
