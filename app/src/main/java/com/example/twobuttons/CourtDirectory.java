package com.example.twobuttons;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;
import android.text.Html;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Court lists: the High Courts (fixed) and the eCourts State -> District -> Court complex chain,
 * fetched live from services.ecourts.gov.in and cached in the app so each list is fetched once.
 */
final class CourtDirectory {
    private CourtDirectory() {}

    static final class Item {
        final String code, name;
        Item(String code, String name) { this.code = code; this.name = name; }
    }

    interface Fetch { List<Item> run() throws IOException; }

    static final String[] HIGH_COURTS = {
            "Allahabad High Court", "Andhra Pradesh High Court", "Bombay High Court",
            "Calcutta High Court", "Chhattisgarh High Court", "Delhi High Court",
            "Gauhati High Court", "Gujarat High Court", "Himachal Pradesh High Court",
            "Jammu & Kashmir and Ladakh High Court", "Jharkhand High Court", "Karnataka High Court",
            "Kerala High Court", "Madhya Pradesh High Court", "Madras High Court",
            "Manipur High Court", "Meghalaya High Court", "Orissa High Court", "Patna High Court",
            "Punjab & Haryana High Court", "Rajasthan High Court", "Sikkim High Court",
            "Telangana High Court", "Tripura High Court", "Uttarakhand High Court"};

    /** Shown only when eCourts can't be reached (no codes, so districts need the internet). */
    private static final String[] OFFLINE_STATES = {
            "Andaman and Nicobar", "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar",
            "Chandigarh", "Chhattisgarh", "Delhi", "Goa", "Gujarat", "Haryana", "Himachal Pradesh",
            "Jammu and Kashmir", "Jharkhand", "Karnataka", "Kerala", "Ladakh", "Lakshadweep",
            "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha",
            "Puducherry", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana",
            "The Dadra And Nagar Haveli And Daman And Diu", "Tripura", "Uttarakhand",
            "Uttar Pradesh", "West Bengal"};

    /** Delhi: judicial district -> court complexes (fixed, no network needed). */
    private static final String[][] DELHI = {
            {"Central", "Tis Hazari Courts", "Rouse Avenue Courts"},
            {"East", "Karkardooma Courts"},
            {"New Delhi", "Patiala House Courts"},
            {"North", "Rohini Courts"},
            {"North East", "Karkardooma Courts"},
            {"North West", "Rohini Courts"},
            {"Shahdara", "Karkardooma Courts"},
            {"South", "Saket Courts"},
            {"South East", "Saket Courts"},
            {"South West", "Dwarka Courts"},
            {"West", "Tis Hazari Courts"}};

    static boolean isDelhi(String state) { return "Delhi".equalsIgnoreCase(state); }

    static List<Item> delhiDistricts() {
        List<Item> l = new ArrayList<>();
        for (String[] d : DELHI) l.add(new Item(d[0], d[0]));
        return l;
    }

    static List<Item> delhiComplexes(String district) {
        List<Item> l = new ArrayList<>();
        for (String[] d : DELHI) {
            if (d[0].equalsIgnoreCase(district)) {
                for (int i = 1; i < d.length; i++) l.add(new Item(d[i], d[i]));
            }
        }
        return l;
    }

    /** All state names, instantly, no codes (codes are looked up from eCourts when needed). */
    static List<Item> offlineStates() {
        List<Item> l = new ArrayList<>();
        for (String s : OFFLINE_STATES) l.add(new Item("", s));
        return l;
    }

    private static final String BASE = "https://services.ecourts.gov.in/ecourtindia_v6/";
    private static final String PAGE = BASE + "?p=casestatus/index&app_token="
            + "c8ae7676034b92766b82c1422bcce4284c02d630e486924b9efff89bfb56187c";
    private static final String UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36";

    private static String token = "";

    // ---------- public, async ----------

    static void states(Activity a, Consumer<List<Item>> ok) {
        load(a, "states", () -> {
            String html = http(PAGE, null);
            Matcher t = Pattern.compile("app_token=([0-9a-f]{64})").matcher(html);
            if (t.find()) token = t.group(1);
            int at = html.indexOf("sess_state_code");
            String part = at < 0 ? html : html.substring(at);
            int end = part.indexOf("</select>");
            if (end > 0) part = part.substring(0, end);
            return parseOptions(part);
        }, ok, err -> {
            List<Item> l = new ArrayList<>();
            for (String s : OFFLINE_STATES) l.add(new Item("", s));
            a.runOnUiThread(() -> ok.accept(l));
        });
    }

    static void districts(Activity a, String stateCode, Consumer<List<Item>> ok, Consumer<String> fail) {
        load(a, "dist_" + stateCode, () -> post("fillDistrict",
                "state_code=" + enc(stateCode)), ok, fail);
    }

    static void complexes(Activity a, String stateCode, String distCode,
                          Consumer<List<Item>> ok, Consumer<String> fail) {
        load(a, "cx_" + stateCode + "_" + distCode, () -> post("fillcomplex",
                "state_code=" + enc(stateCode) + "&dist_code=" + enc(distCode)), ok, fail);
    }

    // ---------- cache + threading ----------

    private static void load(Activity a, String key, Fetch f, Consumer<List<Item>> ok,
                             Consumer<String> fail) {
        List<Item> cached = cached(a, key);
        if (!cached.isEmpty()) { ok.accept(cached); return; }
        Toast.makeText(a, "Loading from eCourts...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            List<Item> out = null;
            String why = "No data from eCourts";
            for (int attempt = 0; attempt < 2 && (out == null || out.isEmpty()); attempt++) {
                try {
                    if (attempt == 1) token = "";          // stale session: start over
                    out = f.run();
                } catch (IOException e) {
                    why = "Can't reach eCourts. Check internet.";
                }
            }
            final List<Item> res = out;
            final String msg = why;
            a.runOnUiThread(() -> {
                if (res == null || res.isEmpty()) { fail.accept(msg); return; }
                store(a, key, res);
                ok.accept(res);
            });
        }, "ecourts").start();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("ecourts_dir", Context.MODE_PRIVATE);
    }

    private static List<Item> cached(Context c, String key) {
        List<Item> l = new ArrayList<>();
        String raw = prefs(c).getString(key, "");
        if (raw.isEmpty()) return l;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONArray p = arr.getJSONArray(i);
                l.add(new Item(p.getString(0), p.getString(1)));
            }
        } catch (JSONException ignored) { l.clear(); }
        return l;
    }

    private static void store(Context c, String key, List<Item> items) {
        JSONArray arr = new JSONArray();
        for (Item it : items) arr.put(new JSONArray().put(it.code).put(it.name));
        prefs(c).edit().putString(key, arr.toString()).apply();
    }

    // ---------- eCourts calls (background thread only) ----------

    private static List<Item> post(String action, String params) throws IOException {
        if (token.isEmpty()) {
            String html = http(PAGE, null);
            Matcher t = Pattern.compile("app_token=([0-9a-f]{64})").matcher(html);
            token = t.find() ? t.group(1) : PAGE.substring(PAGE.indexOf("app_token=") + 10);
        }
        String raw = http(BASE + "?p=casestatus/" + action,
                params + "&ajax_req=true&app_token=" + enc(token));
        String html = raw;
        try {
            JSONObject o = new JSONObject(raw);
            String nt = o.optString("app_token");
            if (!nt.isEmpty()) token = nt;
            StringBuilder sb = new StringBuilder();
            for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                String v = o.optString(it.next());
                if (v.contains("<option")) sb.append(v);
            }
            html = sb.toString();
        } catch (JSONException notJson) { /* plain HTML answer */ }
        return parseOptions(html);
    }

    private static List<Item> parseOptions(String html) {
        List<Item> out = new ArrayList<>();
        Matcher m = Pattern.compile("<option\\s[^>]*?value\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]*))[^>]*>"
                        + "(.*?)(?=</option>|<option|$)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        while (m.find()) {
            String code = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3);
            String name = Html.fromHtml(m.group(4).replaceAll("<[^>]*>", ""),
                    Html.FROM_HTML_MODE_LEGACY).toString().trim();
            if (code == null || code.trim().isEmpty() || name.isEmpty()
                    || name.toLowerCase().startsWith("select")) continue;
            out.add(new Item(code.trim(), name));
        }
        return out;
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    private static String http(String url, String body) throws IOException {
        if (CookieHandler.getDefault() == null) {
            CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        }
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Referer", PAGE);
        c.setRequestProperty("Accept", "*/*");
        if (body != null) {
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            c.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        if (code >= 400) throw new IOException("HTTP " + code);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }
}
