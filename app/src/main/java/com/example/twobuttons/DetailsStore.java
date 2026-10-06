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

    static final String[] CASE_TYPES = {"Civil", "Criminal"};

    static final String[] CIVIL_SUBS = {
            "Suit for Recovery of Money", "Suit for Partition", "Suit for Injunction",
            "Suit for Declaration", "Specific Performance", "Eviction / Rent",
            "Civil Appeal", "Execution Petition", "Other Civil"
    };

    static final String[] CRIMINAL_SUBS = {
            "Bail Application", "Criminal Complaint", "Cheque Bounce (NI Act 138)",
            "Murder", "Theft / Robbery", "Cheating / Fraud", "Domestic Violence",
            "Criminal Appeal", "Criminal Revision", "Other Criminal"
    };

    static final String[] COURTS = {
            "Civil Judge", "Magistrate Court", "District Court", "Sessions Court",
            "Family Court", "Consumer Forum", "Tribunal", "High Court", "Supreme Court", "Other"
    };

    static String[] subsFor(String caseType) {
        return "Criminal".equals(caseType) ? CRIMINAL_SUBS : CIVIL_SUBS;
    }

    static class Details {
        String caseType = "";
        String subType = "";
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
            d.caseType = o.optString("caseType", "");
            d.subType = o.optString("subType", "");
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
            o.put("caseType", d.caseType);
            o.put("subType", d.subType);
            o.put("court", d.court);
            o.put("year", d.year);
            o.put("nextDate", d.nextDate);
            o.put("lawyer", d.lawyer);
            prefs.edit().putString(path, o.toString()).apply();
        } catch (JSONException e) {
            // ignore
        }
    }

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
