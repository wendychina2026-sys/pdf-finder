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

    private final List<List<NewsFeed.Item>> data = new ArrayList<>();
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
        refresh.setOnClickListener(v -> reload(true));
        top.addView(refresh);
        page.addView(top);

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
                render();
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
                shown = PAGE;
                render();
                if (userTap) scroll.scrollTo(0, 0);
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

    // ---------- screen ----------

    private void render() {
        // tabs: only sections that have articles
        tabs.removeAllViews();
        if (sel >= data.size() || data.get(sel).isEmpty()) {
            sel = 0;
            for (int i = 0; i < data.size(); i++) if (!data.get(i).isEmpty()) { sel = i; break; }
        }
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).isEmpty()) continue;
            final int idx = i;
            boolean on = i == sel;
            TextView t = CaseViews.text(this, NewsFeed.SECTIONS[i][0], 14,
                    on ? 0xFFFFFFFF : AppTheme.text(this), on);
            t.setPadding(CaseViews.dp(this, 14), CaseViews.dp(this, 8), CaseViews.dp(this, 14), CaseViews.dp(this, 8));
            t.setBackground(CaseViews.box(this, on ? AppTheme.accent(this) == 0xFFA9B1FF ? 0xFF4A55C8 : 0xFF0B0B8E
                    : AppTheme.card(this), 18, AppTheme.cardStroke(this), 1));
            t.setOnClickListener(v -> { sel = idx; shown = PAGE; render(); scroll.scrollTo(0, 0); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = CaseViews.dp(this, 8);
            tabs.addView(t, lp);
        }
        list.removeAllViews();
        if (sel >= data.size()) return;
        final List<NewsFeed.Item> items = data.get(sel);
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
        TextView dom = CaseViews.text(this, "supremetoday.ai" + (it.date.isEmpty() ? "" : "  \u2022  " + it.date),
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
