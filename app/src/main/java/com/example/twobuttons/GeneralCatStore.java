package com.example.twobuttons;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "General card category Name": the saved names (e.g. MCD, Police, Bank) that General Document Vault
 * cards are filed under. Asked only when a NEW General card is made; attaching to a card never asks it.
 */
final class GeneralCatStore {

    private final Context ctx;
    private final SharedPreferences sp;

    GeneralCatStore(Context c) {
        ctx = c.getApplicationContext();
        sp = ctx.getSharedPreferences("general_card_cats", Context.MODE_PRIVATE);
    }

    List<String> list() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignored) { }
        return out;
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
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || has(n)) return;
        List<String> l = list();
        l.add(n);
        save(l);
    }

    /** Renames the saved name and every General card filed under it. */
    void rename(String old, String neu) {
        List<String> l = list();
        int i = l.indexOf(old);
        if (i >= 0) l.set(i, neu);
        save(l);
        LegalStore ls = new LegalStore(ctx);
        for (LegalStore.Doc d : ls.docs()) {
            if (old.equals(d.genCat)) { d.genCat = neu; ls.saveDoc(d); }
        }
    }

    /** Removes the name from the list; cards already filed under it keep it. */
    void delete(String name) {
        List<String> l = list();
        l.remove(name);
        save(l);
    }

    void swap(int i, int j) {
        List<String> l = list();
        if (i < 0 || j < 0 || i >= l.size() || j >= l.size()) return;
        java.util.Collections.swap(l, i, j);
        save(l);
    }
}
