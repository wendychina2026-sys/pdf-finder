package com.example.twobuttons;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

class MarkStore {

    static final String[] SUBS = {
            "Notice", "Plaint", "Written Statement", "Evidence", "Order / Judgment", "Other"
    };

    private final SharedPreferences prefs;

    MarkStore(Context c) {
        prefs = c.getSharedPreferences("marks", Context.MODE_PRIVATE);
    }

    // page index (0-based) -> sub-category name
    Map<Integer, String> all(String path) {
        Map<Integer, String> out = new HashMap<>();
        try {
            JSONObject o = new JSONObject(prefs.getString(path, "{}"));
            Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                out.put(Integer.parseInt(k), o.getString(k));
            }
        } catch (JSONException | NumberFormatException e) {
            // corrupt data: return what we have
        }
        return out;
    }

    void set(String path, int page, String sub) {
        try {
            JSONObject o = new JSONObject(prefs.getString(path, "{}"));
            o.put(String.valueOf(page), sub);
            prefs.edit().putString(path, o.toString()).apply();
        } catch (JSONException e) {
            // ignore
        }
    }

    void remove(String path, int page) {
        try {
            JSONObject o = new JSONObject(prefs.getString(path, "{}"));
            o.remove(String.valueOf(page));
            prefs.edit().putString(path, o.toString()).apply();
        } catch (JSONException e) {
            // ignore
        }
    }

    /** Reset the whole file: every page mark of this PDF is removed. */
    void clear(String path) {
        prefs.edit().remove(path).apply();
    }

    /** The PDF moved to a new path: its page marks (and attached matter) follow it. */
    void move(Context c, String oldPath, String newPath) {
        if (oldPath.equals(newPath)) return;
        String v = prefs.getString(oldPath, null);
        if (v != null) prefs.edit().remove(oldPath).putString(newPath, v).apply();
        SharedPreferences mp = c.getSharedPreferences("pdf_matters", Context.MODE_PRIVATE);
        String m = mp.getString(oldPath, null);
        if (m != null) mp.edit().remove(oldPath).putString(newPath, m).apply();
    }

    // every PDF path that has marks -> its marks
    Map<String, Map<Integer, String>> everything() {
        Map<String, Map<Integer, String>> out = new HashMap<>();
        for (String path : prefs.getAll().keySet()) {
            Map<Integer, String> m = all(path);
            if (!m.isEmpty()) out.put(path, m);
        }
        return out;
    }

    /** Rename a category on every PDF: pages marked old (or old › sub) become neu (or neu › sub). */
    void renameCategory(String old, String neu) {
        String sep = CategoryPicker.SEP;
        for (String path : prefs.getAll().keySet()) {
            Map<Integer, String> m = all(path);
            boolean changed = false;
            for (Map.Entry<Integer, String> e : m.entrySet()) {
                String v = e.getValue();
                if (v.equals(old)) { e.setValue(neu); changed = true; }
                else if (v.startsWith(old + sep)) { e.setValue(neu + v.substring(old.length())); changed = true; }
            }
            if (changed) save(path, m);
        }
    }

    /** Unmark every page of this PDF whose mark is cat or starts with cat › . */
    void removeCategory(String path, String cat) {
        Map<Integer, String> m = all(path);
        boolean changed = m.values().removeIf(v -> v.equals(cat) || v.startsWith(cat + CategoryPicker.SEP));
        if (changed) save(path, m);
    }

    private void save(String path, Map<Integer, String> m) {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<Integer, String> e : m.entrySet()) o.put(String.valueOf(e.getKey()), e.getValue());
            prefs.edit().putString(path, o.toString()).apply();
        } catch (JSONException e) {
            // ignore
        }
    }
}
