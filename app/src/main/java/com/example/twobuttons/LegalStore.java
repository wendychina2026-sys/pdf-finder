package com.example.twobuttons;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stores Clients, Documents and Reminders (Legal Diary pages).
 * First launch: filled with the sample data. All of it can be edited / deleted.
 */
final class LegalStore {

    static final class CaseLink {
        String title = "", subtitle = "", date = "", status = "Active";
    }

    static final class Client {
        String id = UUID.randomUUID().toString();
        String code = "", name = "", mobile = "", address = "", reference = "";
        String docsNote = "NID, agreements, evidence and other client files";
        long total = 0, paid = 0;
        List<CaseLink> cases = new ArrayList<>();
        long due() { return Math.max(0, total - paid); }
    }

    static final class Doc {
        String id = UUID.randomUUID().toString();
        String name = "", caseName = "", size = "", date = "", category = "Other";
    }

    static final class Reminder {
        String id = UUID.randomUUID().toString();
        String title = "", caseName = "", channel = "WHATSAPP", status = "DELIVERED";
        String sentOn = "", message = "";
    }

    static final String[] DOC_CATEGORIES = {"Petition", "Order", "Evidence", "Other"};
    static final String[] CASE_STATUS = {"Active", "Pending", "Closed"};
    static final String[] CHANNELS = {"WHATSAPP", "SMS", "EMAIL"};
    static final String[] REM_STATUS = {"DELIVERED", "SENT", "PENDING", "FAILED"};

    private final SharedPreferences p;

    LegalStore(Context c) {
        p = c.getSharedPreferences("legal_diary", Context.MODE_PRIVATE);
        if (!p.getBoolean("seeded_v1", false)) seed();
    }

    // ---------------- clients ----------------

    List<Client> clients() {
        List<Client> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("clients", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Client c = new Client();
                c.id = o.optString("id", c.id);
                c.code = o.optString("code");
                c.name = o.optString("name");
                c.mobile = o.optString("mobile");
                c.address = o.optString("address");
                c.reference = o.optString("reference");
                c.docsNote = o.optString("docsNote", c.docsNote);
                c.total = o.optLong("total");
                c.paid = o.optLong("paid");
                JSONArray cs = o.optJSONArray("cases");
                if (cs != null) {
                    for (int j = 0; j < cs.length(); j++) {
                        JSONObject co = cs.getJSONObject(j);
                        CaseLink l = new CaseLink();
                        l.title = co.optString("title");
                        l.subtitle = co.optString("subtitle");
                        l.date = co.optString("date");
                        l.status = co.optString("status", "Active");
                        c.cases.add(l);
                    }
                }
                out.add(c);
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    Client client(String id) {
        if (id == null) return null;
        for (Client c : clients()) if (c.id.equals(id)) return c;
        return null;
    }

    void saveClient(Client c) {
        List<Client> list = clients();
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(c.id)) {
                list.set(i, c);
                found = true;
            }
        }
        if (!found) list.add(c);
        writeClients(list);
    }

    void deleteClient(String id) {
        List<Client> list = clients();
        list.removeIf(c -> c.id.equals(id));
        writeClients(list);
    }

    /** CL-0009 style code, one more than the highest in use. */
    String nextClientCode() {
        int max = 0;
        for (Client c : clients()) {
            String digits = c.code.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                try { max = Math.max(max, Integer.parseInt(digits)); } catch (NumberFormatException ignored) {}
            }
        }
        return String.format(java.util.Locale.US, "CL-%04d", max + 1);
    }

    private void writeClients(List<Client> list) {
        JSONArray a = new JSONArray();
        try {
            for (Client c : list) {
                JSONObject o = new JSONObject();
                o.put("id", c.id);
                o.put("code", c.code);
                o.put("name", c.name);
                o.put("mobile", c.mobile);
                o.put("address", c.address);
                o.put("reference", c.reference);
                o.put("docsNote", c.docsNote);
                o.put("total", c.total);
                o.put("paid", c.paid);
                JSONArray cs = new JSONArray();
                for (CaseLink l : c.cases) {
                    JSONObject co = new JSONObject();
                    co.put("title", l.title);
                    co.put("subtitle", l.subtitle);
                    co.put("date", l.date);
                    co.put("status", l.status);
                    cs.put(co);
                }
                o.put("cases", cs);
                a.put(o);
            }
        } catch (JSONException e) {
            return;
        }
        p.edit().putString("clients", a.toString()).apply();
    }

    // ---------------- documents ----------------

    List<Doc> docs() {
        List<Doc> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("docs", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Doc d = new Doc();
                d.id = o.optString("id", d.id);
                d.name = o.optString("name");
                d.caseName = o.optString("caseName");
                d.size = o.optString("size");
                d.date = o.optString("date");
                d.category = o.optString("category", "Other");
                out.add(d);
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    void saveDoc(Doc d) {
        List<Doc> list = docs();
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(d.id)) {
                list.set(i, d);
                found = true;
            }
        }
        if (!found) list.add(0, d);
        writeDocs(list);
    }

    void deleteDoc(String id) {
        List<Doc> list = docs();
        list.removeIf(d -> d.id.equals(id));
        writeDocs(list);
    }

    private void writeDocs(List<Doc> list) {
        JSONArray a = new JSONArray();
        try {
            for (Doc d : list) {
                JSONObject o = new JSONObject();
                o.put("id", d.id);
                o.put("name", d.name);
                o.put("caseName", d.caseName);
                o.put("size", d.size);
                o.put("date", d.date);
                o.put("category", d.category);
                a.put(o);
            }
        } catch (JSONException e) {
            return;
        }
        p.edit().putString("docs", a.toString()).apply();
    }

    // ---------------- reminders ----------------

    List<Reminder> reminders() {
        List<Reminder> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("reminders", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Reminder r = new Reminder();
                r.id = o.optString("id", r.id);
                r.title = o.optString("title");
                r.caseName = o.optString("caseName");
                r.channel = o.optString("channel", "WHATSAPP");
                r.status = o.optString("status", "DELIVERED");
                r.sentOn = o.optString("sentOn");
                r.message = o.optString("message");
                out.add(r);
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    Reminder reminder(String id) {
        if (id == null) return null;
        for (Reminder r : reminders()) if (r.id.equals(id)) return r;
        return null;
    }

    void saveReminder(Reminder r) {
        List<Reminder> list = reminders();
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(r.id)) {
                list.set(i, r);
                found = true;
            }
        }
        if (!found) list.add(0, r);
        writeReminders(list);
    }

    void deleteReminder(String id) {
        List<Reminder> list = reminders();
        list.removeIf(r -> r.id.equals(id));
        writeReminders(list);
    }

    private void writeReminders(List<Reminder> list) {
        JSONArray a = new JSONArray();
        try {
            for (Reminder r : list) {
                JSONObject o = new JSONObject();
                o.put("id", r.id);
                o.put("title", r.title);
                o.put("caseName", r.caseName);
                o.put("channel", r.channel);
                o.put("status", r.status);
                o.put("sentOn", r.sentOn);
                o.put("message", r.message);
                a.put(o);
            }
        } catch (JSONException e) {
            return;
        }
        p.edit().putString("reminders", a.toString()).apply();
    }

    // ---------------- sample data (same as screenshots) ----------------

    private void seed() {
        // Client Details page
        Client c = new Client();
        c.code = "CL-0008";
        c.name = "Mohammad Sajib";
        c.mobile = "+880 1712 345678";
        c.address = "Dhanmondi, Dhaka 1209";
        c.reference = "Self (Walk-in)";
        c.total = 25000;
        c.paid = 10000;
        CaseLink a = new CaseLink();
        a.title = "Dhamondi Land Dispute";
        a.subtitle = "Civil Suit 234/2026";
        a.date = "12 Mar 2026";
        a.status = "Active";
        CaseLink b = new CaseLink();
        b.title = "Family Matter";
        b.subtitle = "Mediation";
        b.date = "05 Feb 2026";
        b.status = "Pending";
        c.cases.add(a);
        c.cases.add(b);
        List<Client> cl = new ArrayList<>();
        cl.add(c);
        writeClients(cl);

        // Document Library page
        List<Doc> docs = new ArrayList<>();
        docs.add(doc("Divorce Petition.pdf", "Peterson vs. Peterson", "1.2 MB", "14 Mar 2026", "Petition"));
        docs.add(doc("Court Order.docx", "State vs. Miller", "285 KB", "10 Mar 2026", "Order"));
        docs.add(doc("Evidence Photo 1.jpg", "Rao vs. Corporation Ltd.", "3.4 MB", "08 Mar 2026", "Evidence"));
        docs.add(doc("Affidavit.pdf", "Sharma vs. State", "612 KB", "02 Mar 2026", "Other"));
        docs.add(doc("Settlement Agreement.docx", "Khan vs. Ali", "1.1 MB", "28 Feb 2026", "Other"));
        writeDocs(docs);

        // Reminder Details page
        Reminder r = new Reminder();
        r.title = "Hearing Reminder";
        r.caseName = "geeta vs seeta";
        r.channel = "WHATSAPP";
        r.status = "DELIVERED";
        r.sentOn = "09 Mar 2026, 9:00 am";
        r.message = "Hello in,\n\n"
                + "This is a reminder from Adv. Lawyer (via Peshi App).\n\n"
                + "You have a hearing scheduled for:\n"
                + "Case: geeta vs seeta\n"
                + "Date: 09 Mar 2026\n"
                + "Time: 12:01 PM\n"
                + "Court: Court\n\n"
                + "Please be present on time.";
        List<Reminder> rl = new ArrayList<>();
        rl.add(r);
        writeReminders(rl);

        p.edit().putBoolean("seeded_v1", true).apply();
    }

    private static Doc doc(String name, String caseName, String size, String date, String cat) {
        Doc d = new Doc();
        d.name = name;
        d.caseName = caseName;
        d.size = size;
        d.date = date;
        d.category = cat;
        return d;
    }
}
