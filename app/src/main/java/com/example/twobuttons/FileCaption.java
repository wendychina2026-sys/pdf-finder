package com.example.twobuttons;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The name shown to the user for ONE file inside a multi-file document card (e.g. "Hindi copy").
 * The real file and its name on disk are never touched.
 */
final class FileCaption {
    private FileCaption() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("file_captions", Context.MODE_PRIVATE);
    }

    static String get(Context c, String path) {
        return prefs(c).getString(path, "");
    }

    static void set(Context c, String path, String caption) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (caption == null || caption.trim().isEmpty()) e.remove(path);
        else e.putString(path, caption.trim());
        e.apply();
    }

    /** The file moved to a new path: its caption follows it. */
    static void move(Context c, String oldPath, String newPath) {
        if (oldPath.equals(newPath)) return;
        String v = prefs(c).getString(oldPath, null);
        if (v != null) prefs(c).edit().remove(oldPath).putString(newPath, v).apply();
    }
}
