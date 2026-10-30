package com.example.twobuttons;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.Html;
import android.util.LruCache;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Legal news from supremetoday.ai (the site behind the "Supreme Today News" WhatsApp channel).
 * Home page gives the article links per section; each article's og: tags give headline, summary
 * and picture. Only links, headline, summary and picture are used; the article text stays on the site.
 */
final class NewsFeed {

    static final String HOME = "https://supremetoday.ai/";

    /** Tab label, html heading text to look for on the home page. */
    static final String[][] SECTIONS = {
            {"Top Stories", "Top Stories"},
            {"Supreme Court", "Supreme Court News"},
            {"High Courts", "High Court News"},
            {"Banking & Insolvency", "Banking, Finance"},
            {"Business Laws", "Business Regulations"},
            {"Corporate", "Corporate"},
            {"Taxation", "Taxation Laws"},
            {"Tribunals", "Tribunal Court News"},
            {"News Updates", "News Updates"},
    };

    static final class Item {
        String url, slug, title = "", desc = "", img = "", date = "";
        boolean loaded;
    }

    private static final Pattern LINK = Pattern.compile(
            "href=\"(?:https?://(?:www\\.)?supremetoday\\.ai)?/([a-z0-9%\\-]+-(20\\d{9,12}))/?\"");
    private static final Pattern META = Pattern.compile("<meta\\s+[^>]*?>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATTR = Pattern.compile("(property|name|content)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')",
            Pattern.CASE_INSENSITIVE);

    private static final LruCache<String, Bitmap> BITMAPS = new LruCache<String, Bitmap>(
            (int) (Runtime.getRuntime().maxMemory() / 1024 / 8)) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount() / 1024; }
    };

    private NewsFeed() { }

    // ---------- home page -> links per section ----------

    /** One list of article items per entry of SECTIONS (empty list when the section is not found). */
    static List<List<Item>> fetchHome() throws Exception {
        String html = get(HOME, 900_000);
        int n = SECTIONS.length;
        int[] pos = new int[n];
        for (int i = 0; i < n; i++) pos[i] = headingPos(html, SECTIONS[i][1]);
        List<List<Item>> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<Item> list = new ArrayList<>();
            if (pos[i] >= 0) {
                int end = html.length();
                for (int j = 0; j < n; j++) if (pos[j] > pos[i] && pos[j] < end) end = pos[j];
                // the next unknown block (Published Articles, Understanding the Law...) also ends a section
                end = Math.min(end, nextBlock(html, pos[i]));
                list = links(html.substring(pos[i], end));
            }
            out.add(list);
        }
        return out;
    }

    private static int headingPos(String html, String text) {
        String t = Pattern.quote(text);
        Matcher m = Pattern.compile("<h[1-6][^>]*>(?:\\s*<[^>]+>)*\\s*" + t, Pattern.CASE_INSENSITIVE).matcher(html);
        return m.find() ? m.start() : -1;
    }

    private static int nextBlock(String html, int from) {
        int best = html.length();
        for (String h : new String[]{"Published Articles", "Understanding the Law", "Latest Legal News", "Legal Coverage"}) {
            int p = headingPos(html, h);
            if (p > from && p < best) best = p;
        }
        return best;
    }

    private static List<Item> links(String part) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Item> out = new ArrayList<>();
        Matcher m = LINK.matcher(part);
        while (m.find()) {
            String slug = m.group(1);
            if (!seen.add(slug)) continue;
            out.add(fromSlug(slug));
        }
        return out;
    }

    /** An item known only by its link: title and date come from the slug until the page itself is read. */
    static Item fromSlug(String slug) {
        Item it = new Item();
        it.slug = slug;
        it.url = HOME + slug;
        it.title = titleFromSlug(slug);
        Matcher d = Pattern.compile("-(20\\d{9,12})$").matcher(slug);
        if (d.find()) it.date = dateFromSlug(d.group(1));
        return it;
    }

    private static String titleFromSlug(String slug) {
        String s = slug.replaceAll("-20\\d{9,12}$", "").replace('-', ' ');
        StringBuilder sb = new StringBuilder();
        for (String w : s.split(" ")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    /** 20261030088 -> 30 Oct 2026 */
    private static String dateFromSlug(String digits) {
        try {
            int y = Integer.parseInt(digits.substring(0, 4));
            int mo = Integer.parseInt(digits.substring(4, 6));
            int d = Integer.parseInt(digits.substring(6, 8));
            String[] mn = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
            return d + " " + mn[mo - 1] + " " + y;
        } catch (Exception e) {
            return "";
        }
    }

    // ---------- one article: headline, summary, picture ----------

    /** Fills title / desc / img from the article's og: tags (cached on the phone). */
    static void loadMeta(Context c, Item it) {
        if (it.loaded) return;
        SharedPreferences sp = c.getSharedPreferences("news_cache", Context.MODE_PRIVATE);
        try {
            String cached = sp.getString(it.slug, null);
            if (cached != null) {
                JSONObject j = new JSONObject(cached);
                it.title = j.optString("t", it.title);
                it.desc = j.optString("d");
                it.img = j.optString("i");
                it.loaded = true;
                return;
            }
        } catch (Exception ignored) { }
        try {
            String head = get(it.url, 120_000);
            int cut = head.toLowerCase(Locale.ROOT).indexOf("</head>");
            if (cut > 0) head = head.substring(0, cut);
            String t = "", d = "", i = "";
            Matcher m = META.matcher(head);
            while (m.find()) {
                String key = null, val = null;
                Matcher a = ATTR.matcher(m.group());
                while (a.find()) {
                    String k = a.group(1).toLowerCase(Locale.ROOT);
                    String v = a.group(3) != null ? a.group(3) : a.group(4);
                    if (k.equals("content")) val = v; else key = v;
                }
                if (key == null || val == null) continue;
                switch (key) {
                    case "og:title": t = val; break;
                    case "og:description": d = val; break;
                    case "og:image": i = val; break;
                    default: break;
                }
            }
            if (!t.isEmpty()) it.title = plain(t);
            it.desc = plain(d);
            it.img = plain(i);
            it.loaded = true;
            JSONObject j = new JSONObject();
            j.put("t", it.title);
            j.put("d", it.desc);
            j.put("i", it.img);
            sp.edit().putString(it.slug, j.toString()).apply();
            trimCache(sp);
        } catch (Exception ignored) {
            // keep the slug title; the card still opens the article
        }
    }

    private static void trimCache(SharedPreferences sp) {
        if (sp.getAll().size() > 400) sp.edit().clear().apply();      // simple cap, it refills as needed
    }

    private static String plain(String s) {
        if (s == null) return "";
        CharSequence cs = android.os.Build.VERSION.SDK_INT >= 24
                ? Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY) : Html.fromHtml(s);
        return cs.toString().trim();
    }

    // ---------- picture ----------

    static Bitmap cached(String url) { return BITMAPS.get(url); }

    /** Downloads and shrinks a picture to about maxW pixels wide. */
    static Bitmap loadImage(String url, int maxW) {
        Bitmap b = BITMAPS.get(url);
        if (b != null) return b;
        try {
            byte[] data = getBytes(url, 4_000_000);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int s = 1;
            while (o.outWidth / (s * 2) >= maxW) s *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = s;
            b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (b != null) BITMAPS.put(url, b);
        } catch (Exception ignored) { }
        return b;
    }

    // ---------- http ----------

    private static String get(String u, int maxBytes) throws Exception {
        return new String(getBytes(u, maxBytes), "UTF-8");
    }

    private static byte[] getBytes(String u, int maxBytes) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) pdf-finder-news");
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                b.write(buf, 0, n);
                if (b.size() >= maxBytes) break;
            }
            return b.toByteArray();
        }
    }
}
