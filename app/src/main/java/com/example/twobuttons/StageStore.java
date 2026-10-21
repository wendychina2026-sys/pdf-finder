package com.example.twobuttons;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Hearing stages ("Current Stage"). Starts with the built-in list; the user can add, rename and
 * delete any of them. Shared by New Case and the Filter cases sheet.
 */
final class StageStore {

    static final String ALL = "All stages";

    private final SharedPreferences sp;

    StageStore(Context c) {
        sp = c.getSharedPreferences("hearing_stages", Context.MODE_PRIVATE);
    }

    /** Stages in order, without "All stages". */
    List<String> list() {
        String raw = sp.getString("list", null);
        List<String> out = new ArrayList<>();
        if (raw == null) {
            out.addAll(Arrays.asList(AllCasesActivity.STAGES).subList(1, AllCasesActivity.STAGES.length));
            return out;
        }
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) {
        }
        return out;
    }

    /** "All stages" first, for filters. */
    String[] withAll() {
        List<String> l = list();
        l.add(0, ALL);
        return l.toArray(new String[0]);
    }

    private void save(List<String> l) {
        JSONArray a = new JSONArray();
        for (String s : l) a.put(s);
        sp.edit().putString("list", a.toString()).apply();
    }

    boolean has(String name) {
        for (String s : list()) if (s.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    void add(String name) {
        List<String> l = list();
        l.add(name);
        save(l);
    }

    void rename(String old, String neu) {
        List<String> l = list();
        int i = l.indexOf(old);
        if (i >= 0) l.set(i, neu);
        save(l);
    }

    void delete(String name) {
        List<String> l = list();
        l.remove(name);
        save(l);
    }
}
