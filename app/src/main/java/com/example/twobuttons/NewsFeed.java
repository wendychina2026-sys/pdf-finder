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

    // Verdictum (verdictum.in): tab label, listing page. Each listing is read when its tab is opened.
    static final String HOME_V = "https://www.verdictum.in/";
    static final String[][] V_SECTIONS = {
            {"Latest", "https://www.verdictum.in/latest-news"},
            {"Supreme Court", "https://www.verdictum.in/court-updates/supreme-court"},
            {"High Courts", "https://www.verdictum.in/court-updates/high-courts"},
            {"Top Stories", "https://www.verdictum.in/top-stories"},
            {"News", "https://www.verdictum.in/news"},
            {"Columns", "https://www.verdictum.in/columns"},
    };

    // LiveLaw Hindi (hindi.livelaw.in): tab label, listing page. Same CMS as Verdictum; read when its tab is opened.
    static final String HOME_H = "https://hindi.livelaw.in/";
    static final String[][] H_SECTIONS = {
            {"\u092e\u0941\u0916\u094d\u092f \u0938\u0941\u0930\u094d\u0916\u093f\u092f\u093e\u0902", "https://hindi.livelaw.in/category/top-stories"},
            {"\u0924\u093e\u091c\u093e \u0916\u092c\u0930\u0947\u0902", "https://hindi.livelaw.in/category/news-updates"},
            {"\u0938\u0941\u092a\u094d\u0930\u0940\u092e \u0915\u094b\u0930\u094d\u091f", "https://hindi.livelaw.in/supreme-court"},
            {"\u0939\u093e\u0908\u0915\u094b\u0930\u094d\u091f", "https://hindi.livelaw.in/high-court"},
            {"\u0909\u092a\u092d\u094b\u0915\u094d\u0924\u093e \u092e\u093e\u092e\u0932\u0947", "https://hindi.livelaw.in/consumer-cases"},
            {"\u091c\u093e\u0928\u093f\u090f \u0939\u092e\u093e\u0930\u093e \u0915\u093e\u0928\u0942\u0928", "https://hindi.livelaw.in/know-the-law"},
            {"\u0938\u094d\u0924\u0902\u092d", "https://hindi.livelaw.in/category/columns"},
    };

    static final class Item {
        String url, slug, domain = "supremetoday.ai", title = "", desc = "", img = "", date = "";
        boolean loaded;
    }

    private static final Pattern LINK = Pattern.compile(
            "href=\"(?:https?://(?:www\\.)?supremetoday\\.ai)?/([a-z0-9%\\-]+-(20\\d{9,12}))/?\"");
    private static final Pattern VLINK = Pattern.compile(
            "href=\"(?:https?://(?:www\\.)?verdictum\\.in)?/([a-z0-9\\-]+)/([a-z0-9%\\-]+-(\\d{6,8}))/?\"");
    // hindi.livelaw.in/category/news-updates/<english-slug>-162196 or /supreme-court/<slug>-123456
    private static final Pattern HLINK = Pattern.compile(
            "href=\"(?:https?://hindi\\.livelaw\\.in)?/((?:[a-z0-9\\-]+/){1,2}([a-z0-9%\\-]+-(\\d{5,8})))/?\"");
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

    /** Article links of one Verdictum listing page, newest first. */
    static List<Item> fetchVerdictum(String listUrl) throws Exception {
        String html = get(listUrl, 800_000);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Item> out = new ArrayList<>();
        Matcher m = VLINK.matcher(html);
        while (m.find()) {
            String sec = m.group(1);
            if (sec.equals("court-updates") || sec.equals("tags") || sec.equals("videos")
                    || sec.equals("videoes") || sec.equals("latest-news")) continue;
            if (!seen.add(m.group(3))) continue;
            out.add(fromVUrl(HOME_V + sec + "/" + m.group(2)));
        }
        return out;
    }

    /** Article links of one LiveLaw Hindi listing page, newest first. */
    static List<Item> fetchLiveLawHindi(String listUrl) throws Exception {
        String html = get(listUrl, 900_000);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<Item> out = new ArrayList<>();
        Matcher m = HLINK.matcher(html);
        while (m.find()) {
            String path = m.group(1);
            if (path.startsWith("tags/") || path.startsWith("author/") || path.startsWith("videos/")
                    || path.startsWith("webinar/") || path.startsWith("images/") || path.startsWith("h-upload/")
                    || path.startsWith("theme_leo/")) continue;
            if (!seen.add(m.group(3))) continue;
            out.add(fromHUrl(HOME_H + path));
        }
        return out;
    }

    /** A LiveLaw Hindi item known only by its link (title is the English slug until the page is read). */
    static Item fromHUrl(String url) {
        Item it = new Item();
        it.url = url;
        it.domain = "hindi.livelaw.in";
        Matcher d = Pattern.compile("/([a-z0-9%\\-]+-(\\d{5,8}))/?$").matcher(url);
        String slug = url, id = url;
        if (d.find()) { slug = d.group(1); id = d.group(2); }
        it.slug = "h" + id;
        it.title = titleFromSlug(slug);
        return it;
    }

    /** A Verdictum item known only by its link. */
    static Item fromVUrl(String url) {
        Item it = new Item();
        it.url = url;
        it.domain = "verdictum.in";
        Matcher d = Pattern.compile("/([a-z0-9%\\-]+-(\\d{6,8}))/?$").matcher(url);
        String slug = url, id = url;
        if (d.find()) { slug = d.group(1); id = d.group(2); }
        it.slug = "v" + id;
        it.title = titleFromSlug(slug);
        return it;
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
        String s = slug.replaceAll("-\\d{6,14}$", "").replace('-', ' ');
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
                it.date = j.optString("dt", it.date);
                it.loaded = true;
                return;
            }
        } catch (Exception ignored) { }
        try {
            String head = get(it.url, 120_000);
            int cut = head.toLowerCase(Locale.ROOT).indexOf("</head>");
            if (cut > 0) head = head.substring(0, cut);
            String t = "", d = "", i = "", pub = "";
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
                    case "article:published_time": pub = val; break;
                    default: break;
                }
            }
            if (!t.isEmpty()) it.title = plain(t);
            it.desc = plain(d);
            it.img = plain(i);
            if (pub.length() >= 10) {
                String dt = dateFromSlug(pub.substring(0, 4) + pub.substring(5, 7) + pub.substring(8, 10));
                if (!dt.isEmpty()) it.date = dt;
            }
            it.loaded = true;
            JSONObject j = new JSONObject();
            j.put("t", it.title);
            j.put("d", it.desc);
            j.put("i", it.img);
            j.put("dt", it.date);
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
