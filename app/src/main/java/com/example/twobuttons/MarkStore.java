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
}
