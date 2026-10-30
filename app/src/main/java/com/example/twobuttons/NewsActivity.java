package com.example.twobuttons;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Legal News: the Supreme Today News channel as cards (picture, headline, summary, link), read from
 * supremetoday.ai. Tabs per section, pull the refresh icon to reload, tap a card to open the article.
 */
public class NewsActivity extends BaseActivity {

    private static final int PAGE = 20;

    private final List<List<NewsFeed.Item>> data = new ArrayList<>();      // Supreme Today, all sections at once
    private final List<List<NewsFeed.Item>> vData = new ArrayList<>();     // Verdictum, one listing per tab
    private final boolean[] vLoaded = new boolean[NewsFeed.V_SECTIONS.length];
    private final List<List<NewsFeed.Item>> hData = new ArrayList<>();     // LiveLaw Hindi, one listing per tab
    private final boolean[] hLoaded = new boolean[NewsFeed.H_SECTIONS.length];
    // Bar & Bench Hindi, Law Trend Hindi, Lawyer Guruji: one listing per tab
    private final List<List<List<NewsFeed.Item>>> xData = new ArrayList<>();
    private final boolean[][] xLoaded = new boolean[NewsFeed.X_SECTIONS.length][];
    private int src;                                                        // 0 Supreme Today, 1 Verdictum, 2 LiveLaw Hindi, 3 Bar & Bench Hindi, 4 Law Trend Hindi, 5 Lawyer Guruji
    private LinearLayout srcRow;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private boolean dead;
    private int sel;
    private int shown = PAGE;

    private LinearLayout tabs;
    private LinearLayout list;
    private TextView status;
    private ScrollView scroll;

    private static final class Holder {
        TextView title, desc;
        ImageView img;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().hide();
        final int ink = AppTheme.text(this);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(AppTheme.bg(this));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(CaseViews.dp(this, 8), CaseViews.dp(this, 10), CaseViews.dp(this, 8), CaseViews.dp(this, 6));
        ImageView back = CaseViews.icon(this, R.drawable.ic_arrow_back, ink, 44);
        back.setPadding(CaseViews.dp(this, 10), CaseViews.dp(this, 10), CaseViews.dp(this, 10), CaseViews.dp(this, 10));
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        top.addView(back);
        TextView title = CaseViews.text(this, "Legal News", 20, ink, true);
        title.setPadding(CaseViews.dp(this, 6), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ImageView refresh = CaseViews.icon(this, R.drawable.ic_refresh, AppTheme.accent(this), 44);
        refresh.setPadding(CaseViews.dp(this, 10), CaseViews.dp(this, 10), CaseViews.dp(this, 10), CaseViews.dp(this, 10));
        refresh.setContentDescription("Refresh");
        refresh.setOnClickListener(v -> { if (src == 0) reload(true); else if (src == 1) fetchV(sel, true); else if (src == 2) fetchH(sel, true); else fetchX(src - 3, sel, true); });
        top.addView(refresh);
        page.addView(top);

        srcRow = new LinearLayout(this);
        srcRow.setPadding(CaseViews.dp(this, 12), 0, CaseViews.dp(this, 12), CaseViews.dp(this, 6));
        HorizontalScrollView srcScroll = new HorizontalScrollView(this);
        srcScroll.setHorizontalScrollBarEnabled(false);
        srcScroll.addView(srcRow);
        page.addView(srcScroll);

        for (int i = 0; i < NewsFeed.V_SECTIONS.length; i++) vData.add(new ArrayList<NewsFeed.Item>());
        for (int i = 0; i < NewsFeed.H_SECTIONS.length; i++) hData.add(new ArrayList<NewsFeed.Item>());
        for (int s = 0; s < NewsFeed.X_SECTIONS.length; s++) {
            List<List<NewsFeed.Item>> tabsData = new ArrayList<>();
            for (int i = 0; i < NewsFeed.X_SECTIONS[s].length; i++) tabsData.add(new ArrayList<NewsFeed.Item>());
            xData.add(tabsData);
            xLoaded[s] = new boolean[NewsFeed.X_SECTIONS[s].length];
        }

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        tabs = new LinearLayout(this);
        tabs.setPadding(CaseViews.dp(this, 12), 0, CaseViews.dp(this, 12), CaseViews.dp(this, 8));
        hs.addView(tabs);
        page.addView(hs);

        status = CaseViews.text(this, "", 14, AppTheme.sub(this), false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(CaseViews.dp(this, 20), CaseViews.dp(this, 8), CaseViews.dp(this, 20), CaseViews.dp(this, 8));
        status.setVisibility(View.GONE);
        page.addView(status);

        scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(CaseViews.dp(this, 12), CaseViews.dp(this, 4), CaseViews.dp(this, 12), CaseViews.dp(this, 24));
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);

        loadCachedHome();
        loadCachedV();
        loadCachedH();
        loadCachedX();
        renderSources();
        reload(false);
    }

    @Override
    protected void onDestroy() {
        dead = true;
        pool.shutdownNow();
        super.onDestroy();
    }

    // ---------- data ----------

    private void loadCachedHome() {
        try {
            String s = getSharedPreferences("news_cache", MODE_PRIVATE).getString("home", null);
            if (s == null) return;
            JSONArray all = new JSONArray(s);
            List<List<NewsFeed.Item>> d = new ArrayList<>();
            for (int i = 0; i < all.length(); i++) {
                JSONArray a = all.getJSONArray(i);
                List<NewsFeed.Item> l = new ArrayList<>();
                for (int k = 0; k < a.length(); k++) l.add(NewsFeed.fromSlug(a.getString(k)));
                d.add(l);
            }
            if (d.size() == NewsFeed.SECTIONS.length) {
                data.clear();
                data.addAll(d);
                if (src == 0) render();
            }
        } catch (Exception ignored) { }
    }

    private void reload(boolean userTap) {
        status.setText("Loading news...");
        status.setVisibility(View.VISIBLE);
        new Thread(() -> {
            List<List<NewsFeed.Item>> d = null;
            String err = null;
            try {
                d = NewsFeed.fetchHome();
                int total = 0;
                for (List<NewsFeed.Item> l : d) total += l.size();
                if (total == 0) err = "No articles found. The site layout may have changed.";
            } catch (Exception e) {
                err = "Cannot reach supremetoday.ai. Check internet.";
            }
            final List<List<NewsFeed.Item>> fd = d;
            final String fErr = err;
            runOnUiThread(() -> {
                if (dead) return;
                if (fErr != null) {
                    status.setText(data.isEmpty() ? fErr : fErr + " Showing saved list.");
                    status.setVisibility(View.VISIBLE);
                    return;
                }
                status.setVisibility(View.GONE);
                data.clear();
                data.addAll(fd);
                saveHome();
                if (src == 0) {
                    shown = PAGE;
                    render();
                    if (userTap) scroll.scrollTo(0, 0);
                }
            });
        }).start();
    }

    private void saveHome() {
        try {
            JSONArray all = new JSONArray();
            for (List<NewsFeed.Item> l : data) {
                JSONArray a = new JSONArray();
                for (NewsFeed.Item it : l) a.put(it.slug);
                all.put(a);
            }
            getSharedPreferences("news_cache", MODE_PRIVATE).edit().putString("home", all.toString()).apply();
        } catch (Exception ignored) { }
    }

    private List<List<NewsFeed.Item>> cur() {
        return src == 0 ? data : src == 1 ? vData : src == 2 ? hData : xData.get(src - 3);
    }

    private String label(int i) {
        return src == 0 ? NewsFeed.SECTIONS[i][0] : src == 1 ? NewsFeed.V_SECTIONS[i][0]
                : src == 2 ? NewsFeed.H_SECTIONS[i][0] : NewsFeed.X_SECTIONS[src - 3][i][0];
    }

    private void loadCachedX() {
        try {
            android.content.SharedPreferences sp = getSharedPreferences("news_cache", MODE_PRIVATE);
            for (int s = 0; s < xData.size(); s++) {
                for (int i = 0; i < xData.get(s).size(); i++) {
                    String v = sp.getString("xtab" + s + "_" + i, null);
                    if (v == null) continue;
                    JSONArray arr = new JSONArray(v);
                    List<NewsFeed.Item> l = new ArrayList<>();
                    for (int k = 0; k < arr.length(); k++) l.add(NewsFeed.fromXUrl(arr.getString(k), s));
                    xData.get(s).set(i, l);
                }
            }
        } catch (Exception ignored) { }
    }

    /** Reads one tab of Bar & Bench Hindi / Law Trend Hindi / Lawyer Guruji (s = 0, 1, 2). force = refresh icon. */
    private void fetchX(final int s, final int idx, boolean force) {
        if (!force && xLoaded[s][idx]) return;
        final String name = NewsFeed.X_NAMES[s];
        status.setText("Loading " + name + "...");
        status.setVisibility(View.VISIBLE);
        new Thread(() -> {
            List<NewsFeed.Item> l = null;
            String err = null;
            try {
                l = NewsFeed.fetchX(s, NewsFeed.X_SECTIONS[s][idx][1]);
                if (l.isEmpty()) err = "No articles found on " + name + ". The site layout may have changed.";
            } catch (Exception e) {
                err = "Cannot reach " + NewsFeed.X_DOMAIN[s] + ". Check internet.";
            }
            final List<NewsFeed.Item> fl = l;
            final String fErr = err;
            runOnUiThread(() -> {
                if (dead) return;
                if (fErr != null) {
                    status.setText(xData.get(s).get(idx).isEmpty() ? fErr : fErr + " Showing saved list.");
                    status.setVisibility(View.VISIBLE);
                    return;
                }
                status.setVisibility(View.GONE);
                xData.get(s).set(idx, fl);
                xLoaded[s][idx] = true;
                try {
                    JSONArray arr = new JSONArray();
                    for (NewsFeed.Item it : fl) arr.put(it.url);
                    getSharedPreferences("news_cache", MODE_PRIVATE).edit().putString("xtab" + s + "_" + idx, arr.toString()).apply();
                } catch (Exception ignored) { }
                if (src == s + 3 && sel == idx) { shown = PAGE; render(); }
            });
        }).start();
    }

    private void loadCachedH() {
        try {
            android.content.SharedPreferences sp = getSharedPreferences("news_cache", MODE_PRIVATE);
            for (int i = 0; i < hData.size(); i++) {
                String s = sp.getString("htab" + i, null);
                if (s == null) continue;
                JSONArray a = new JSONArray(s);
                List<NewsFeed.Item> l = new ArrayList<>();
                for (int k = 0; k < a.length(); k++) l.add(NewsFeed.fromHUrl(a.getString(k)));
                hData.set(i, l);
            }
        } catch (Exception ignored) { }
    }

    /** Reads one LiveLaw Hindi listing (the tab being opened). force = refresh icon. */
    private void fetchH(final int idx, boolean force) {
        if (!force && hLoaded[idx]) return;
        status.setText("Loading LiveLaw Hindi...");
        status.setVisibility(View.VISIBLE);
        new Thread(() -> {
            List<NewsFeed.Item> l = null;
            String err = null;
            try {
                l = NewsFeed.fetchLiveLawHindi(NewsFeed.H_SECTIONS[idx][1]);
                if (l.isEmpty()) err = "No articles found on LiveLaw Hindi. The site layout may have changed.";
            } catch (Exception e) {
                err = "Cannot reach hindi.livelaw.in. Check internet.";
            }
            final List<NewsFeed.Item> fl = l;
            final String fErr = err;
            runOnUiThread(() -> {
                if (dead) return;
                if (fErr != null) {
                    status.setText(hData.get(idx).isEmpty() ? fErr : fErr + " Showing saved list.");
                    status.setVisibility(View.VISIBLE);
                    return;
                }
                status.setVisibility(View.GONE);
                hData.set(idx, fl);
                hLoaded[idx] = true;
                try {
                    JSONArray a = new JSONArray();
                    for (NewsFeed.Item it : fl) a.put(it.url);
                    getSharedPreferences("news_cache", MODE_PRIVATE).edit().putString("htab" + idx, a.toString()).apply();
                } catch (Exception ignored) { }
                if (src == 2 && sel == idx) { shown = PAGE; render(); }
            });
        }).start();
    }

    private void loadCachedV() {
        try {
            android.content.SharedPreferences sp = getSharedPreferences("news_cache", MODE_PRIVATE);
            for (int i = 0; i < vData.size(); i++) {
                String s = sp.getString("vtab" + i, null);
                if (s == null) continue;
                JSONArray a = new JSONArray(s);
                List<NewsFeed.Item> l = new ArrayList<>();
                for (int k = 0; k < a.length(); k++) l.add(NewsFeed.fromVUrl(a.getString(k)));
                vData.set(i, l);
            }
        } catch (Exception ignored) { }
    }

    /** Reads one Verdictum listing (the tab being opened). force = refresh icon. */
    private void fetchV(final int idx, boolean force) {
        if (!force && vLoaded[idx]) return;
        status.setText("Loading Verdictum...");
        status.setVisibility(View.VISIBLE);
        new Thread(() -> {
            List<NewsFeed.Item> l = null;
            String err = null;
            try {
                l = NewsFeed.fetchVerdictum(NewsFeed.V_SECTIONS[idx][1]);
                if (l.isEmpty()) err = "No articles found on Verdictum. The site layout may have changed.";
            } catch (Exception e) {
                err = "Cannot reach verdictum.in. Check internet.";
            }
            final List<NewsFeed.Item> fl = l;
            final String fErr = err;
            runOnUiThread(() -> {
                if (dead) return;
                if (fErr != null) {
                    status.setText(vData.get(idx).isEmpty() ? fErr : fErr + " Showing saved list.");
                    status.setVisibility(View.VISIBLE);
                    return;
                }
                status.setVisibility(View.GONE);
                vData.set(idx, fl);
                vLoaded[idx] = true;
                try {
                    JSONArray a = new JSONArray();
                    for (NewsFeed.Item it : fl) a.put(it.url);
                    getSharedPreferences("news_cache", MODE_PRIVATE).edit().putString("vtab" + idx, a.toString()).apply();
                } catch (Exception ignored) { }
                if (src == 1 && sel == idx) { shown = PAGE; render(); }
            });
        }).start();
    }

    private void renderSources() {
        srcRow.removeAllViews();
        String[] names = {"Supreme Today", "Verdictum", "LiveLaw Hindi",
                NewsFeed.X_NAMES[0], NewsFeed.X_NAMES[1], NewsFeed.X_NAMES[2]};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            boolean on = i == src;
            TextView t = CaseViews.text(this, names[i], 14, on ? 0xFFFFFFFF : AppTheme.text(this), true);
            t.setPadding(CaseViews.dp(this, 14), CaseViews.dp(this, 7), CaseViews.dp(this, 14), CaseViews.dp(this, 7));
            t.setBackground(CaseViews.box(this, on ? 0xFF731818 : AppTheme.card(this), 18, AppTheme.cardStroke(this), 1));
            t.setOnClickListener(v -> {
                if (src == idx) return;
                src = idx;
                sel = 0;
                shown = PAGE;
                status.setVisibility(View.GONE);
                renderSources();
                render();
                scroll.scrollTo(0, 0);
                if (src == 1) fetchV(sel, false);
                else if (src == 2) fetchH(sel, false);
                else if (src >= 3) fetchX(src - 3, sel, false);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = CaseViews.dp(this, 8);
            srcRow.addView(t, lp);
        }
    }

    // ---------- screen ----------

    private void render() {
        final List<List<NewsFeed.Item>> d = cur();
        tabs.removeAllViews();
        if (src == 0 && (sel >= d.size() || d.get(sel).isEmpty())) {
            sel = 0;
            for (int i = 0; i < d.size(); i++) if (!d.get(i).isEmpty()) { sel = i; break; }
        }
        for (int i = 0; i < d.size(); i++) {
            if (src == 0 && d.get(i).isEmpty()) continue;       // Verdictum / LiveLaw Hindi tabs are read when opened, so all show
            final int idx = i;
            boolean on = i == sel;
            TextView t = CaseViews.text(this, label(i), 14, on ? 0xFFFFFFFF : AppTheme.text(this), on);
            t.setPadding(CaseViews.dp(this, 14), CaseViews.dp(this, 8), CaseViews.dp(this, 14), CaseViews.dp(this, 8));
            t.setBackground(CaseViews.box(this, on ? (AppTheme.isDark(this) ? 0xFF4A55C8 : 0xFF0B0B8E)
                    : AppTheme.card(this), 18, AppTheme.cardStroke(this), 1));
            t.setOnClickListener(v -> {
                sel = idx;
                shown = PAGE;
                render();
                scroll.scrollTo(0, 0);
                if (src == 1) fetchV(idx, false);
                else if (src == 2) fetchH(idx, false);
                else if (src >= 3) fetchX(src - 3, idx, false);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = CaseViews.dp(this, 8);
            tabs.addView(t, lp);
        }
        list.removeAllViews();
        if (sel >= d.size()) return;
        final List<NewsFeed.Item> items = d.get(sel);
        int n = Math.min(shown, items.size());
        for (int i = 0; i < n; i++) list.addView(card(items.get(i)));
        if (n < items.size()) {
            TextView more = CaseViews.text(this, "Show more", 15, AppTheme.accent(this), true);
            more.setGravity(Gravity.CENTER);
            more.setPadding(0, CaseViews.dp(this, 14), 0, CaseViews.dp(this, 14));
            more.setOnClickListener(v -> { shown += PAGE; render(); });
            list.addView(more);
        }
    }

    private View card(final NewsFeed.Item it) {
        final int w = getResources().getDisplayMetrics().widthPixels - CaseViews.dp(this, 24);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(CaseViews.box(this, AppTheme.card(this), 14, AppTheme.cardStroke(this), 1));
        card.setClipToOutline(true);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = CaseViews.dp(this, 8);
        card.setLayoutParams(clp);

        final Holder h = new Holder();
        h.img = new ImageView(this);
        h.img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        h.img.setBackgroundColor(AppTheme.isDark(this) ? 0xFF2A2C3A : 0xFFE3E5F0);
        card.addView(h.img, new LinearLayout.LayoutParams(w, w * 9 / 16));

        int pad = CaseViews.dp(this, 12);
        h.title = CaseViews.text(this, it.title, 16, AppTheme.text(this), true);
        h.title.setPadding(pad, pad, pad, 0);
        card.addView(h.title);
        h.desc = CaseViews.text(this, it.desc, 13, AppTheme.sub(this), false);
        h.desc.setPadding(pad, CaseViews.dp(this, 4), pad, 0);
        h.desc.setMaxLines(3);
        h.desc.setEllipsize(TextUtils.TruncateAt.END);
        h.desc.setVisibility(it.desc.isEmpty() ? View.GONE : View.VISIBLE);
        card.addView(h.desc);

        LinearLayout foot = new LinearLayout(this);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding(pad, CaseViews.dp(this, 8), CaseViews.dp(this, 4), CaseViews.dp(this, 6));
        TextView dom = CaseViews.text(this, it.domain + (it.date.isEmpty() ? "" : "  \u2022  " + it.date),
                12, AppTheme.accent(this), false);
        foot.addView(dom, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ImageView share = CaseViews.icon(this, R.drawable.ic_share, AppTheme.sub(this), 36);
        share.setPadding(CaseViews.dp(this, 8), CaseViews.dp(this, 8), CaseViews.dp(this, 8), CaseViews.dp(this, 8));
        share.setContentDescription("Share");
        share.setOnClickListener(v -> {
            Intent s = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, it.title + "\n" + it.url);
            startActivity(Intent.createChooser(s, "Share"));
        });
        foot.addView(share);
        card.addView(foot);

        card.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(it.url)));
            } catch (Exception e) {
                Toast.makeText(this, "No app to open the link", Toast.LENGTH_SHORT).show();
            }
        });

        // headline, summary and picture come from the article page; fill the card when they arrive
        if (it.loaded) fill(it, h, w); else {
            try {
                pool.execute(() -> {
                    NewsFeed.loadMeta(this, it);
                    Bitmap b = it.img.isEmpty() ? null : NewsFeed.loadImage(it.img, 720);
                    runOnUiThread(() -> { if (!dead) fill(it, h, w, b); });
                });
            } catch (Exception ignored) { }
        }
        return card;
    }

    private void fill(NewsFeed.Item it, Holder h, int w) {
        h.title.setText(it.title);
        h.desc.setText(it.desc);
        h.desc.setVisibility(it.desc.isEmpty() ? View.GONE : View.VISIBLE);
        Bitmap b = it.img.isEmpty() ? null : NewsFeed.cached(it.img);
        if (b != null) h.img.setImageBitmap(b);
        else if (!it.img.isEmpty()) {
            try {
                pool.execute(() -> {
                    final Bitmap bb = NewsFeed.loadImage(it.img, 720);
                    runOnUiThread(() -> { if (!dead && bb != null) h.img.setImageBitmap(bb); });
                });
            } catch (Exception ignored) { }
        }
    }

    private void fill(NewsFeed.Item it, Holder h, int w, Bitmap b) {
        h.title.setText(it.title);
        h.desc.setText(it.desc);
        h.desc.setVisibility(it.desc.isEmpty() ? View.GONE : View.VISIBLE);
        if (b != null) h.img.setImageBitmap(b);
    }
}
