package com.example.twobuttons;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class DetailsStore {

    static class Details {
        String caseName = "";    // free text, searched from dashboard
        String caseType = "";
        String subType = "";
        String subSubType = "";
        String court = "";
        int year = 0;            // 0 = not set
        String nextDate = "";    // yyyy-MM-dd or ""
        String lawyer = "";
    }

    static String formatDate(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso);
            return new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(d);
        } catch (ParseException e) {
            return iso;
        }
    }

    static String summary(Details d) {
        StringBuilder sb = new StringBuilder();
        add(sb, d.caseType);
        add(sb, d.subType);
        add(sb, d.subSubType);
        add(sb, d.court);
        if (d.year > 0) add(sb, String.valueOf(d.year));
        String line1 = sb.toString();

        StringBuilder sb2 = new StringBuilder();
        if (!d.lawyer.isEmpty()) sb2.append("Lawyer: ").append(d.lawyer);
        if (!d.nextDate.isEmpty()) {
            if (sb2.length() > 0) sb2.append("  |  ");
            sb2.append("Next date: ").append(formatDate(d.nextDate));
        }
        if (line1.isEmpty()) return sb2.toString();
        if (sb2.length() == 0) return line1;
        return line1 + "\n" + sb2;
    }

    private static void add(StringBuilder sb, String s) {
        if (s == null || s.isEmpty()) return;
        if (sb.length() > 0) sb.append("  |  ");
        sb.append(s);
    }

    private final SharedPreferences prefs;
    private final SharedPreferences lawyerPrefs;

    DetailsStore(Context c) {
        prefs = c.getSharedPreferences("details", Context.MODE_PRIVATE);
        lawyerPrefs = c.getSharedPreferences("lawyers", Context.MODE_PRIVATE);
    }

    Details get(String path) {
        String raw = prefs.getString(path, null);
        if (raw == null) return null;
        try {
            JSONObject o = new JSONObject(raw);
            Details d = new Details();
            d.caseName = o.optString("caseName", "");
            d.caseType = o.optString("caseType", "");
            d.subType = o.optString("subType", "");
            d.subSubType = o.optString("subSubType", "");
            if ("Civil".equals(d.caseType)) d.caseType = CaseOptions.CIVIL;
            d.court = o.optString("court", "");
            d.year = o.optInt("year", 0);
            d.nextDate = o.optString("nextDate", "");
            d.lawyer = o.optString("lawyer", "");
            return d;
        } catch (JSONException e) {
            return null;
        }
    }

    void save(String path, Details d) {
        try {
            JSONObject o = new JSONObject();
            o.put("caseName", d.caseName);
            o.put("caseType", d.caseType);
            o.put("subType", d.subType);
            o.put("subSubType", d.subSubType);
            o.put("court", d.court);
            o.put("year", d.year);
            o.put("nextDate", d.nextDate);
            o.put("lawyer", d.lawyer);
            prefs.edit().putString(path, o.toString()).apply();
        } catch (JSONException e) {
            // ignore
        }
    }

    // ----- bulk helpers used by OptionsEditorActivity -----

    int countWhere(java.util.function.Predicate<Details> m) {
        int n = 0;
        for (String path : prefs.getAll().keySet()) {
            Details d = get(path);
            if (d != null && m.test(d)) n++;
        }
        return n;
    }

    int updateWhere(java.util.function.Predicate<Details> m,
                    java.util.function.Consumer<Details> change) {
        int n = 0;
        for (String path : prefs.getAll().keySet()) {
            Details d = get(path);
            if (d != null && m.test(d)) {
                change.accept(d);
                save(path, d);
                n++;
            }
        }
        return n;
    }

    int renameType(String old, String n) {
        return updateWhere(d -> old.equals(d.caseType), d -> d.caseType = n);
    }

    int renameSub(String type, String old, String n) {
        return updateWhere(d -> type.equals(d.caseType) && old.equals(d.subType),
                d -> d.subType = n);
    }

    int renameSubSub(String type, String sub, String old, String n) {
        return updateWhere(d -> type.equals(d.caseType) && sub.equals(d.subType)
                && old.equals(d.subSubType), d -> d.subSubType = n);
    }

    int renameCourt(String old, String n) {
        return updateWhere(d -> old.equals(d.court), d -> d.court = n);
    }

    int usesType(String t) { return countWhere(d -> t.equals(d.caseType)); }

    int usesSub(String t, String s) {
        return countWhere(d -> t.equals(d.caseType) && s.equals(d.subType));
    }

    int usesSubSub(String t, String s, String ss) {
        return countWhere(d -> t.equals(d.caseType) && s.equals(d.subType)
                && ss.equals(d.subSubType));
    }

    int usesCourt(String c) { return countWhere(d -> c.equals(d.court)); }

    Map<String, Details> everything() {
        Map<String, Details> out = new HashMap<>();
        for (String path : prefs.getAll().keySet()) {
            Details d = get(path);
            if (d != null) out.put(path, d);
        }
        return out;
    }

    List<String> lawyers() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(lawyerPrefs.getString("list", "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        } catch (JSONException e) {
            // return what we have
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    void addLawyer(String name) {
        name = name == null ? "" : name.trim();
        if (name.isEmpty()) return;
        List<String> list = lawyers();
        for (String s : list) if (s.equalsIgnoreCase(name)) return;
        list.add(name);
        JSONArray arr = new JSONArray();
        for (String s : list) arr.put(s);
        lawyerPrefs.edit().putString("list", arr.toString()).apply();
    }
}
