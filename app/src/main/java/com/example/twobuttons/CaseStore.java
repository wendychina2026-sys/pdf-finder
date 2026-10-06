package com.example.twobuttons;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class CaseStore {

    static final String[] TYPES = {
            "Civil Case", "Criminal Case" 
    };

    private final SharedPreferences prefs;

    CaseStore(Context c) {
        prefs = c.getSharedPreferences("cases", Context.MODE_PRIVATE);
    }

    List<CaseEntry> load() {
        List<CaseEntry> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new CaseEntry(o.getString("id"), o.getString("type"),
                        o.getString("title"), o.getString("number")));
            }
        } catch (JSONException e) {
            // corrupt data: return what we have
        }
        return out;
    }

    private void save(List<CaseEntry> list) {
        JSONArray arr = new JSONArray();
        try {
            for (CaseEntry e : list) {
                JSONObject o = new JSONObject();
                o.put("id", e.id);
                o.put("type", e.type);
                o.put("title", e.title);
                o.put("number", e.number);
                arr.put(o);
            }
        } catch (JSONException ex) {
            return;
        }
        prefs.edit().putString("list", arr.toString()).apply();
    }

    void add(String type, String title, String number) {
        List<CaseEntry> list = load();
        list.add(new CaseEntry(UUID.randomUUID().toString(), type, title, number));
        save(list);
    }

    void update(CaseEntry changed) {
        List<CaseEntry> list = load();
        for (CaseEntry e : list) {
            if (e.id.equals(changed.id)) {
                e.type = changed.type;
                e.title = changed.title;
                e.number = changed.number;
            }
        }
        save(list);
    }

    void delete(String id) {
        List<CaseEntry> list = load();
        list.removeIf(e -> e.id.equals(id));
        save(list);
    }
}
