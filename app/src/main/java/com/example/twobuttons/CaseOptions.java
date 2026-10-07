package com.example.twobuttons;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * One place for all dropdown lists:
 *   case types -> sub case types -> sub-sub case types, and court names.
 *
 * EDIT IN CODE : change the DEFAULTS section below, then bump DEFAULTS_VERSION.
 *                (Bumping replaces any list edits made inside the app.)
 * EDIT IN APP  : Case details page -> "Edit dropdown lists" button
 *                (OptionsEditorActivity). Edits are saved on the phone. Renames also update saved file details.
 *
 * NOTE: rename in app updates saved file details; renaming in code does not.
 */
class CaseOptions {

    static final int DEFAULTS_VERSION = 1;

    static final String CIVIL = "Civil and family case";
    static final String CRIMINAL = "Criminal";

    // =====================================================================
    // DEFAULTS - edit here
    // =====================================================================

    private static Map<String, Map<String, List<String>>> defaultTree() {
        Map<String, Map<String, List<String>>> t = new LinkedHashMap<>();

        Map<String, List<String>> civil = new LinkedHashMap<>();
        civil.put("Family and matrimonial", list(
                "Divorce",
                "Judicial separation",
                "Maintenance and alimony",
                "Child custody, guardianship, and visitation",
                "Restitution of conjugal rights",
                "Domestic-violence applications for protection, residence, or monetary relief",
                "Matrimonial property and related disputes"));
        civil.put("Money and contracts", list(
                "Recovery of money or debt",
                "Contract disputes and damages",
                "Specific performance of contracts",
                "Cheque-related civil recovery claims",
                "Accounts and business disputes"));
        civil.put("Property and tenancy", list(
                "Ownership, title, and possession disputes",
                "Partition and inheritance",
                "Landlord\u2013tenant and eviction cases",
                "Rent disputes",
                "Easements and boundary disputes",
                "Injunctions to prevent or require an action"));
        civil.put("Accident, employment, and other civil claims", list(
                "Motor Accident Claims Tribunal (MACT) compensation claims",
                "Employment and labour disputes",
                "Consumer complaints",
                "Probate, succession, and letters of administration",
                "Declaratory suits",
                "Civil appeals and execution of decrees"));
        t.put(CIVIL, civil);

        Map<String, List<String>> crim = new LinkedHashMap<>();
        crim.put("Offences against women and children", list(
                "Rape and other sexual offences",
                "POCSO offences",
                "Sexual harassment, stalking, and voyeurism",
                "Dowry-related offences",
                "Cruelty by husband or relatives",
                "Kidnapping, abduction, and trafficking",
                "Domestic-violence-related criminal offences"));
        crim.put("Offences against persons", list(
                "Murder and culpable homicide",
                "Attempt to murder",
                "Hurt and grievous hurt",
                "Assault and criminal intimidation",
                "Wrongful restraint or confinement",
                "Abduction and kidnapping"));
        crim.put("Property and financial offences", list(
                "Cheating",
                "Criminal breach of trust",
                "Theft, robbery, and dacoity",
                "Extortion",
                "Forgery and use of forged documents",
                "Criminal trespass",
                "Cyber fraud and identity theft"));
        crim.put("Drugs and other special-law offences", list(
                "NDPS offences",
                "Arms and ammunition offences",
                "Prevention of Corruption Act cases",
                "Unlawful Activities (Prevention) Act cases",
                "Excise and other regulatory offences"));
        crim.put("Other common criminal matters", list(
                "Defamation",
                "Public-order offences",
                "Traffic and motor-vehicle offences",
                "Cheque dishonour cases under the Negotiable Instruments Act",
                "Bail, anticipatory bail, and cancellation of bail",
                "Appeals, revisions, and complaints"));
        t.put(CRIMINAL, crim);

        return t;
    }

    private static List<String> defaultCourts() {
        return list(
                "Civil Judge", "Magistrate Court", "District Court", "Sessions Court",
                "Family Court", "Consumer Forum", "Tribunal", "High Court", "Supreme Court",
                "Tis Hazari (Central and West)",
                "Patiala House (New Delhi)",
                "Karkardooma (East, North-East, and Shahdara)",
                "Rohini (North and North-West)",
                "Dwarka (South-West)",
                "Saket (South and South-East)",
                "Rouse Avenue (District-court complex)",
                "Delhi High Court",
                "Delhi Supreme Court",
                "Other");
    }

    // =====================================================================
    // engine - no need to touch below
    // =====================================================================

    private static List<String> list(String... a) {
        return new ArrayList<>(Arrays.asList(a));
    }

    private final SharedPreferences prefs;
    private Map<String, Map<String, List<String>>> tree;
    private List<String> courts;

    CaseOptions(Context c) {
        prefs = c.getSharedPreferences("case_options", Context.MODE_PRIVATE);
        load();
    }

    // ----- read -----

    List<String> types() { return new ArrayList<>(tree.keySet()); }

    List<String> subs(String type) {
        Map<String, List<String>> m = tree.get(type);
        return m == null ? new ArrayList<String>() : new ArrayList<>(m.keySet());
    }

    List<String> subSubs(String type, String sub) {
        Map<String, List<String>> m = tree.get(type);
        List<String> l = m == null ? null : m.get(sub);
        return l == null ? new ArrayList<String>() : new ArrayList<>(l);
    }

    /** Sub-subs for a sub name when the case type is unknown. */
    List<String> subSubsAnyType(String sub) {
        for (Map<String, List<String>> m : tree.values()) {
            if (m.containsKey(sub)) return new ArrayList<>(m.get(sub));
        }
        return new ArrayList<>();
    }

    /** Every sub-sub type. type empty/null = all case types together (civil + criminal). */
    List<String> allSubSubs(String type) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Map.Entry<String, Map<String, List<String>>> e : tree.entrySet()) {
            if (type != null && !type.isEmpty() && !type.equals(e.getKey())) continue;
            for (List<String> l : e.getValue().values()) out.addAll(l);
        }
        return new ArrayList<>(out);
    }

    List<String> allSubs() {
        List<String> out = new ArrayList<>();
        for (Map<String, List<String>> m : tree.values()) out.addAll(m.keySet());
        return out;
    }

    List<String> courts() { return new ArrayList<>(courts); }

    static String[] arr(List<String> l) { return l.toArray(new String[0]); }

    // ----- edit -----

    boolean addType(String n) {
        n = clean(n);
        if (n.isEmpty() || has(tree.keySet(), n)) return false;
        tree.put(n, new LinkedHashMap<String, List<String>>());
        save();
        return true;
    }

    boolean renameType(String old, String n) {
        n = clean(n);
        if (n.isEmpty() || !tree.containsKey(old)) return false;
        if (!n.equalsIgnoreCase(old) && has(tree.keySet(), n)) return false;
        Map<String, Map<String, List<String>>> nt = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, List<String>>> e : tree.entrySet()) {
            nt.put(e.getKey().equals(old) ? n : e.getKey(), e.getValue());
        }
        tree = nt;
        save();
        return true;
    }

    void deleteType(String n) { tree.remove(n); save(); }

    boolean addSub(String type, String n) {
        n = clean(n);
        Map<String, List<String>> m = tree.get(type);
        if (m == null || n.isEmpty() || has(m.keySet(), n)) return false;
        m.put(n, new ArrayList<String>());
        save();
        return true;
    }

    boolean renameSub(String type, String old, String n) {
        n = clean(n);
        Map<String, List<String>> m = tree.get(type);
        if (m == null || n.isEmpty() || !m.containsKey(old)) return false;
        if (!n.equalsIgnoreCase(old) && has(m.keySet(), n)) return false;
        Map<String, List<String>> nm = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : m.entrySet()) {
            nm.put(e.getKey().equals(old) ? n : e.getKey(), e.getValue());
        }
        tree.put(type, nm);
        save();
        return true;
    }

    void deleteSub(String type, String n) {
        Map<String, List<String>> m = tree.get(type);
        if (m != null) { m.remove(n); save(); }
    }

    boolean addSubSub(String type, String sub, String n) {
        n = clean(n);
        List<String> l = listFor(type, sub);
        if (l == null || n.isEmpty() || has(l, n)) return false;
        l.add(n);
        save();
        return true;
    }

    boolean renameSubSub(String type, String sub, String old, String n) {
        n = clean(n);
        List<String> l = listFor(type, sub);
        if (l == null || n.isEmpty()) return false;
        int i = l.indexOf(old);
        if (i < 0) return false;
        if (!n.equalsIgnoreCase(old) && has(l, n)) return false;
        l.set(i, n);
        save();
        return true;
    }

    void deleteSubSub(String type, String sub, String n) {
        List<String> l = listFor(type, sub);
        if (l != null) { l.remove(n); save(); }
    }

    boolean addCourt(String n) {
        n = clean(n);
        if (n.isEmpty() || has(courts, n)) return false;
        courts.add(n);
        save();
        return true;
    }

    boolean renameCourt(String old, String n) {
        n = clean(n);
        int i = courts.indexOf(old);
        if (n.isEmpty() || i < 0) return false;
        if (!n.equalsIgnoreCase(old) && has(courts, n)) return false;
        courts.set(i, n);
        save();
        return true;
    }

    void deleteCourt(String n) { courts.remove(n); save(); }

    void resetToDefaults() {
        prefs.edit().clear().apply();
        load();
    }

    // ----- internals -----

    private List<String> listFor(String type, String sub) {
        Map<String, List<String>> m = tree.get(type);
        return m == null ? null : m.get(sub);
    }

    private static String clean(String s) { return s == null ? "" : s.trim(); }

    private static boolean has(Iterable<String> it, String n) {
        for (String s : it) if (s.equalsIgnoreCase(n)) return true;
        return false;
    }

    private void load() {
        int ver = prefs.getInt("v", 0);
        String raw = prefs.getString("json", null);
        if (raw != null && ver >= DEFAULTS_VERSION) {
            try {
                JSONObject o = new JSONObject(raw);
                Map<String, Map<String, List<String>>> t = new LinkedHashMap<>();
                JSONArray ta = o.getJSONArray("types");
                for (int i = 0; i < ta.length(); i++) {
                    JSONObject to = ta.getJSONObject(i);
                    Map<String, List<String>> sm = new LinkedHashMap<>();
                    JSONArray sa = to.getJSONArray("subs");
                    for (int j = 0; j < sa.length(); j++) {
                        JSONObject so = sa.getJSONObject(j);
                        List<String> items = new ArrayList<>();
                        JSONArray ia = so.getJSONArray("items");
                        for (int k = 0; k < ia.length(); k++) items.add(ia.getString(k));
                        sm.put(so.getString("name"), items);
                    }
                    t.put(to.getString("name"), sm);
                }
                List<String> cl = new ArrayList<>();
                JSONArray ca = o.getJSONArray("courts");
                for (int i = 0; i < ca.length(); i++) cl.add(ca.getString(i));
                tree = t;
                courts = cl;
                return;
            } catch (JSONException ignored) {
                // fall through to defaults
            }
        }
        tree = defaultTree();
        courts = defaultCourts();
        prefs.edit().clear().apply();
    }

    private void save() {
        try {
            JSONArray ta = new JSONArray();
            for (Map.Entry<String, Map<String, List<String>>> te : tree.entrySet()) {
                JSONArray sa = new JSONArray();
                for (Map.Entry<String, List<String>> se : te.getValue().entrySet()) {
                    JSONArray ia = new JSONArray();
                    for (String s : se.getValue()) ia.put(s);
                    sa.put(new JSONObject().put("name", se.getKey()).put("items", ia));
                }
                ta.put(new JSONObject().put("name", te.getKey()).put("subs", sa));
            }
            JSONArray ca = new JSONArray();
            for (String c : courts) ca.put(c);
            JSONObject o = new JSONObject().put("types", ta).put("courts", ca);
            prefs.edit().putInt("v", DEFAULTS_VERSION).putString("json", o.toString()).apply();
        } catch (JSONException ignored) {
        }
    }
}
