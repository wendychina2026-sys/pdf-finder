package com.example.twobuttons;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Opposing advocates used before: name, mobile and the saved photo of the visiting card. */
final class AdvocateStore {

    static final class Adv {
        String name = "", mobile = "", card = "";
    }

    private final SharedPreferences sp;

    AdvocateStore(Context c) {
        sp = c.getSharedPreferences("opposing_advocates", Context.MODE_PRIVATE);
    }

    List<Adv> list() {
        List<Adv> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Adv v = new Adv();
                v.name = o.optString("name");
                v.mobile = o.optString("mobile");
                v.card = o.optString("card");
                out.add(v);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Add or update by name. An empty mobile / card never wipes a saved one. */
    void save(String name, String mobile, String card) {
        if (name == null || name.trim().isEmpty()) return;
        List<Adv> all = list();
        Adv hit = null;
        for (Adv v : all) if (v.name.toLowerCase(Locale.ROOT).equals(name.trim().toLowerCase(Locale.ROOT))) hit = v;
        if (hit == null) {
            hit = new Adv();
            all.add(0, hit);
        }
        hit.name = name.trim();
        if (mobile != null && !mobile.isEmpty()) hit.mobile = mobile;
        if (card != null && !card.isEmpty()) hit.card = card;
        JSONArray a = new JSONArray();
        try {
            for (Adv v : all) {
                JSONObject o = new JSONObject();
                o.put("name", v.name);
                o.put("mobile", v.mobile);
                o.put("card", v.card);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        sp.edit().putString("list", a.toString()).apply();
    }
}
