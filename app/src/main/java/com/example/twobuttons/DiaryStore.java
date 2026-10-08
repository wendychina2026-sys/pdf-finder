package com.example.twobuttons;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Cases (home / weekly / hearings), case-list items, courts, judges, notes. Sample data on first run. */
final class DiaryStore {

    static final class Hearing {
        String date = "", note = "";
    }

    static final class HCase {
        String id = UUID.randomUUID().toString();
        String first = "", second = "", number = "", type = "", subType = "";
        String court = "", judge = "", room = "", proceeding = "", representing = "";
        String prev = "", next = "";           // dd/MM/yyyy
        long billTotal = 0, billPaid = 0;
        boolean completed = false;
        List<Hearing> hearings = new ArrayList<>();
        String title() { return first + " vs. " + second; }
        long pending() { return Math.max(0, billTotal - billPaid); }
    }

    static final class LCase {
        String id = UUID.randomUUID().toString();
        String title = "", court = "", caseType = "", caseNo = "";
        String contact = "", phone = "", onBehalf = "", respondent = "";
        String prev = "", adjourn = "";
        int steps = 1;
    }

    static final class Judge {
        String id = UUID.randomUUID().toString();
        String name = "", court = "", room = "";
    }

    static final String[] CASE_TYPES = {"Civil", "Criminal", "Family", "Revenue"};
    private static final SimpleDateFormat DMY = new SimpleDateFormat("dd/MM/yyyy", Locale.US);

    private final SharedPreferences p;

    DiaryStore(Context c) {
        p = c.getSharedPreferences("diary", Context.MODE_PRIVATE);
        if (!p.getBoolean("seeded_v1", false)) seed();
    }

    // ---------------- dates ----------------

    static Date parse(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try { return DMY.parse(s.trim()); } catch (ParseException e) { return null; }
    }

    static String fmt(Date d) { return DMY.format(d); }

    static Date today() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    static Date weekEnd() {
        Calendar c = Calendar.getInstance();
        c.setTime(today());
        c.set(Calendar.DAY_OF_WEEK, c.getFirstDayOfWeek());
        c.add(Calendar.DAY_OF_YEAR, 6);
        return c.getTime();
    }

    // ---------------- home cases ----------------

    List<HCase> cases() {
        List<HCase> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("cases", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                HCase c = new HCase();
                c.id = o.optString("id", c.id);
                c.first = o.optString("first"); c.second = o.optString("second");
                c.number = o.optString("number"); c.type = o.optString("type");
                c.subType = o.optString("subType"); c.court = o.optString("court");
                c.judge = o.optString("judge"); c.room = o.optString("room");
                c.proceeding = o.optString("proceeding"); c.representing = o.optString("representing");
                c.prev = o.optString("prev"); c.next = o.optString("next");
                c.billTotal = o.optLong("billTotal"); c.billPaid = o.optLong("billPaid");
                c.completed = o.optBoolean("completed");
                JSONArray hs = o.optJSONArray("hearings");
                if (hs != null) for (int j = 0; j < hs.length(); j++) {
                    JSONObject ho = hs.getJSONObject(j);
                    Hearing h = new Hearing();
                    h.date = ho.optString("date"); h.note = ho.optString("note");
                    c.hearings.add(h);
                }
                out.add(c);
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    HCase caseById(String id) {
        if (id == null) return null;
        for (HCase c : cases()) if (c.id.equals(id)) return c;
        return null;
    }

    void saveCase(HCase c) {
        List<HCase> list = cases();
        boolean found = false;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(c.id)) { list.set(i, c); found = true; }
        if (!found) list.add(c);
        writeCases(list);
    }

    void deleteCase(String id) {
        List<HCase> list = cases();
        list.removeIf(c -> c.id.equals(id));
        writeCases(list);
    }

    private void writeCases(List<HCase> list) {
        JSONArray a = new JSONArray();
        try {
            for (HCase c : list) {
                JSONObject o = new JSONObject();
                o.put("id", c.id); o.put("first", c.first); o.put("second", c.second);
                o.put("number", c.number); o.put("type", c.type); o.put("subType", c.subType);
                o.put("court", c.court); o.put("judge", c.judge); o.put("room", c.room);
                o.put("proceeding", c.proceeding); o.put("representing", c.representing);
                o.put("prev", c.prev); o.put("next", c.next);
                o.put("billTotal", c.billTotal); o.put("billPaid", c.billPaid);
                o.put("completed", c.completed);
                JSONArray hs = new JSONArray();
                for (Hearing h : c.hearings) {
                    JSONObject ho = new JSONObject();
                    ho.put("date", h.date); ho.put("note", h.note);
                    hs.put(ho);
                }
                o.put("hearings", hs);
                a.put(o);
            }
        } catch (JSONException e) {
            return;
        }
        p.edit().putString("cases", a.toString()).apply();
    }

    // ---------------- case list items ----------------

    List<LCase> listCases() {
        List<LCase> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("lcases", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                LCase c = new LCase();
                c.id = o.optString("id", c.id);
                c.title = o.optString("title"); c.court = o.optString("court");
                c.caseType = o.optString("caseType"); c.caseNo = o.optString("caseNo");
                c.contact = o.optString("contact"); c.phone = o.optString("phone");
                c.onBehalf = o.optString("onBehalf"); c.respondent = o.optString("respondent");
                c.prev = o.optString("prev"); c.adjourn = o.optString("adjourn");
                c.steps = o.optInt("steps", 1);
                out.add(c);
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    void saveListCase(LCase c) {
        List<LCase> list = listCases();
        boolean found = false;
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(c.id)) { list.set(i, c); found = true; }
        if (!found) list.add(c);
        writeListCases(list);
    }

    void deleteListCase(String id) {
        List<LCase> list = listCases();
        list.removeIf(c -> c.id.equals(id));
        writeListCases(list);
    }

    private void writeListCases(List<LCase> list) {
        JSONArray a = new JSONArray();
        try {
            for (LCase c : list) {
                JSONObject o = new JSONObject();
                o.put("id", c.id); o.put("title", c.title); o.put("court", c.court);
                o.put("caseType", c.caseType); o.put("caseNo", c.caseNo);
                o.put("contact", c.contact); o.put("phone", c.phone);
                o.put("onBehalf", c.onBehalf); o.put("respondent", c.respondent);
                o.put("prev", c.prev); o.put("adjourn", c.adjourn); o.put("steps", c.steps);
                a.put(o);
            }
        } catch (JSONException e) {
            return;
        }
        p.edit().putString("lcases", a.toString()).apply();
    }

    // ---------------- courts (tiers) ----------------

    List<String> courts() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("courts", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (JSONException ignored) {
        }
        return out;
    }

    void saveCourts(List<String> list) {
        JSONArray a = new JSONArray();
        for (String s : list) a.put(s);
        p.edit().putString("courts", a.toString()).apply();
    }

    // ---------------- judges ----------------

    List<Judge> judges() {
        List<Judge> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("judges", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Judge j = new Judge();
                j.id = o.optString("id", j.id);
                j.name = o.optString("name"); j.court = o.optString("court"); j.room = o.optString("room");
                out.add(j);
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    void saveJudges(List<Judge> list) {
        JSONArray a = new JSONArray();
        try {
            for (Judge j : list) {
                JSONObject o = new JSONObject();
                o.put("id", j.id); o.put("name", j.name); o.put("court", j.court); o.put("room", j.room);
                a.put(o);
            }
        } catch (JSONException e) {
            return;
        }
        p.edit().putString("judges", a.toString()).apply();
    }

    // ---------------- notes ----------------

    String notes() { return p.getString("notes", ""); }

    void setNotes(String s) { p.edit().putString("notes", s).apply(); }

    // ---------------- sample data (same as screenshots) ----------------

    private static Hearing h(String date, String note) {
        Hearing x = new Hearing();
        x.date = date;
        x.note = note;
        return x;
    }

    private static HCase hc(String first, String second, String number, String type, String sub,
                            String court, String proceeding, String prev, String next) {
        HCase c = new HCase();
        c.first = first; c.second = second; c.number = number; c.type = type; c.subType = sub;
        c.court = court; c.proceeding = proceeding; c.prev = prev; c.next = next;
        return c;
    }

    private void seed() {
        // Manage Court
        List<String> courts = new ArrayList<>();
        courts.add("Delhi High Court");
        courts.add("Ahmedabad High Court");
        courts.add("Delhi Civil Court");
        courts.add("Surat High Court");
        courts.add("Udaipur Family Court");
        saveCourts(courts);

        // Case List
        List<LCase> ls = new ArrayList<>();
        LCase a = new LCase();
        a.title = "Delhi high court"; a.court = "Delhi"; a.caseType = "Criminal"; a.caseNo = "2025/2025";
        a.contact = "Mr Harish Salveji"; a.phone = "9898012345"; a.onBehalf = "Amit Shah";
        a.respondent = "Mukulji R."; a.prev = "12/10/2025"; a.adjourn = "03/01/2026"; a.steps = 2;
        LCase b = new LCase();
        b.title = "Pension Issue Complaint"; b.court = "Delhi"; b.caseType = "General"; b.caseNo = "1003/2026";
        b.contact = "Kapil Sharma"; b.phone = "9898012399"; b.onBehalf = "Mr. G. Subramanian";
        b.respondent = "Mr S. Khurshid"; b.prev = ""; b.adjourn = "03/01/2026"; b.steps = 1;
        ls.add(a);
        ls.add(b);
        writeListCases(ls);

        // Hearings timeline case
        List<HCase> cs = new ArrayList<>();
        HCase s = hc("Smith", "Anderson", "CIV-2026-0123", "Civil", "Contract Dispute",
                "Civil Court", "", "", "");
        s.representing = "Smith";
        s.billTotal = 100000;
        s.billPaid = 70000;
        s.completed = true;
        s.hearings.add(h("20 Jun 2026", "Final arguments heard. Judgement reserved."));
        s.hearings.add(h("05 May 2026", "Evidence submitted. Hearing continued."));
        s.hearings.add(h("10 Apr 2026", "Adjourned at request of defence."));
        s.hearings.add(h("15 Mar 2026", "First hearing \u2013 parties present. Next date set for arguments."));
        cs.add(s);

        // Home / weekly cases
        cs.add(hc("adsf", "Adds", "Asdf", "Criminal", "Writ", "Civil Court", "Asdfasdf", "", "28/04/2026"));
        cs.add(hc("Asdf", "Asdfgh", "", "Civil", "", "Civil Court", "", "23/04/2026", "28/04/2026"));
        cs.add(hc("Usman", "Ahmed", "", "Civil", "", "Civil Court", "", "27/04/2026", "28/04/2026"));
        writeCases(cs);

        p.edit().putBoolean("seeded_v1", true).apply();
    }
}
