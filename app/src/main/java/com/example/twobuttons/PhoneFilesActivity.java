package com.example.twobuttons;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The phone's files in the style of a system file manager: Music / Video / Photo / Apps / Archive / Docs /
 * Webpage / Others with counts, a storage bar, Clean Files, and Download / Files tabs.
 * With EXTRA_PICK it lets the user tick files and returns their paths (used by Transfer Files).
 */
public class PhoneFilesActivity extends BaseActivity {

    static final String EXTRA_PICK = "pick";
    static final String EXTRA_PATHS = "paths";

    private static final String[] CAT_NAMES =
            {"Music", "Video", "Photo", "Apps", "Archive", "Docs", "Webpage", "Others"};
    private static final int[] CAT_ICONS = {
            R.drawable.ic_cat_music, R.drawable.ic_cat_video, R.drawable.ic_cat_photo, R.drawable.ic_cat_apps,
            R.drawable.ic_cat_archive, R.drawable.ic_cat_docs, R.drawable.ic_cat_web, R.drawable.ic_cat_others};

    private static final Set<String> AUDIO = set("mp3", "m4a", "wav", "flac", "aac", "ogg", "oga", "opus", "amr", "wma", "mid");
    private static final Set<String> APPS = set("apk", "xapk", "apks", "apkm");
    private static final Set<String> ARCHIVE = set("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz");
    private static final Set<String> DOCS = set("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv",
            "rtf", "odt", "ods", "odp", "epub", "md", "json", "xml");
    private static final Set<String> WEB = set("html", "htm", "mht", "mhtml");

    private static Set<String> set(String... a) { return new HashSet<>(Arrays.asList(a)); }

    static final class Entry {
        String path;
        String name;
        long size;
        long modified;
        int cat;
        boolean dir;
    }

    // ---- data ----
    private final List<List<Entry>> cats = new ArrayList<>();
    private final List<Entry> everything = new ArrayList<>();
    private boolean loaded;
    private boolean loading;

    // ---- state ----
    private static final int HOME = 0, CATEGORY = 1, FOLDER = 2, SEARCH = 3;
    private int page = HOME;
    private int curCat;
    private File folderRoot;
    private File curDir;
    private boolean downloadTab;
    private boolean searching;
    private int pageBeforeSearch = HOME;
    private boolean pick;
    private final Set<String> picked = new LinkedHashSet<>();
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy", Locale.US);

    // ---- views ----
    private LinearLayout topBar;
    private FrameLayout content;
    private LinearLayout pickBar;
    private TextView pickCount;
    private LinearLayout tabBar;
    private EditText searchBox;
    private List<Entry> shownList = new ArrayList<>();
    private BaseAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pick = getIntent().getBooleanExtra(EXTRA_PICK, false);
        for (int i = 0; i < CAT_NAMES.length; i++) cats.add(new ArrayList<>());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AppTheme.bg(this));

        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(8), dp(6), dp(8), dp(6));
        root.addView(topBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        pickBar = new LinearLayout(this);
        pickBar.setOrientation(LinearLayout.HORIZONTAL);
        pickBar.setGravity(Gravity.CENTER_VERTICAL);
        pickBar.setPadding(dp(16), dp(10), dp(16), dp(10));
        pickBar.setBackgroundColor(AppTheme.card(this));
        pickCount = CaseViews.text(this, "", 15, AppTheme.text(this), true);
        pickBar.addView(pickCount, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView clear = CaseViews.text(this, "Clear", 15, AppTheme.accent(this), true);
        clear.setPadding(dp(12), dp(8), dp(12), dp(8));
        clear.setOnClickListener(v -> { picked.clear(); refreshPickBar(); notifyList(); });
        pickBar.addView(clear);
        TextView send = CaseViews.text(this, "Send", 15, AppTheme.isDark(this) ? 0xFF121212 : 0xFFFFFFFF, true);
        send.setGravity(Gravity.CENTER);
        send.setPadding(dp(22), dp(10), dp(22), dp(10));
        send.setBackground(CaseViews.box(this, AppTheme.accent(this), 10, 0, 0));
        send.setOnClickListener(v -> finishPick());
        pickBar.addView(send);
        pickBar.setVisibility(View.GONE);
        root.addView(pickBar);

        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackgroundColor(AppTheme.bg(this));
        root.addView(tabBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));

        setContentView(root);
        buildTop();
        buildTabs();
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!loaded) ensureLoaded();
    }

    // ------------------------------------------------------------------ helpers

    private int dp(int v) { return CaseViews.dp(this, v); }

    private static String ext(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static int classify(String name, String mime) {
        String e = ext(name);
        if (APPS.contains(e)) return 3;
        if ((mime != null && mime.startsWith("audio/")) || AUDIO.contains(e)) return 0;
        if (mime != null && mime.startsWith("video/")) return 1;
        if (mime != null && mime.startsWith("image/")) return 2;
        if (ARCHIVE.contains(e)) return 4;
        if (DOCS.contains(e)) return 5;
        if (WEB.contains(e)) return 6;
        return 7;
    }

    private static String sizeText(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(Locale.US, "%.1f KB", b / 1024.0);
        if (b < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", b / 1048576.0);
        return String.format(Locale.US, "%.2f GB", b / 1073741824.0);
    }

    private boolean hasAccess() { return new PermissionHelper(this).hasAccess(); }

    // ------------------------------------------------------------------ loading

    private void ensureLoaded() {
        if (loading || loaded || !hasAccess()) {
            if (!hasAccess()) render();
            return;
        }
        loading = true;
        new Thread(() -> {
            List<List<Entry>> res = new ArrayList<>();
            for (int i = 0; i < CAT_NAMES.length; i++) res.add(new ArrayList<>());
            List<Entry> every = new ArrayList<>();
            try (Cursor c = getContentResolver().query(
                    MediaStore.Files.getContentUri("external"),
                    new String[]{MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.SIZE,
                            MediaStore.Files.FileColumns.DATE_MODIFIED, MediaStore.Files.FileColumns.MIME_TYPE},
                    MediaStore.Files.FileColumns.SIZE + ">0", null,
                    MediaStore.Files.FileColumns.DATE_MODIFIED + " DESC")) {
                if (c != null) {
                    while (c.moveToNext()) {
                        String path = c.getString(0);
                        if (path == null) continue;
                        int slash = path.lastIndexOf('/');
                        String name = slash >= 0 ? path.substring(slash + 1) : path;
                        if (name.startsWith(".") || path.contains("/.")) continue;
                        String mime = c.getString(3);
                        if (mime == null && !new File(path).isFile()) continue;     // folders have no type
                        Entry e = new Entry();
                        e.path = path;
                        e.name = name;
                        e.size = c.getLong(1);
                        e.modified = c.getLong(2) * 1000L;
                        e.cat = classify(name, mime);
                        res.get(e.cat).add(e);
                        every.add(e);
                    }
                }
            } catch (Exception ignored) {
                // no media access: the counts stay at 0
            }
            runOnUiThread(() -> {
                for (int i = 0; i < cats.size(); i++) {
                    cats.get(i).clear();
                    cats.get(i).addAll(res.get(i));
                }
                everything.clear();
                everything.addAll(every);
                loaded = true;
                loading = false;
                render();
            });
        }, "phone-files-load").start();
    }

    // ------------------------------------------------------------------ top bar and tabs

    private void buildTop() {
        topBar.removeAllViews();
        ImageView back = CaseViews.iconPlain(this, R.drawable.ic_arrow_back, AppTheme.text(this), 26);
        back.setPadding(dp(8), dp(8), dp(8), dp(8));
        back.setContentDescription("Back");
        back.setOnClickListener(v -> onBackPressed());
        topBar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        if (searching) {
            searchBox = new EditText(this);
            searchBox.setSingleLine(true);
            searchBox.setHint("Search files");
            searchBox.setTextColor(AppTheme.text(this));
            searchBox.setHintTextColor(AppTheme.sub(this));
            searchBox.setBackground(null);
            searchBox.setTextSize(17);
            searchBox.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void afterTextChanged(Editable s) { renderSearch(s.toString()); }
            });
            topBar.addView(searchBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            ImageView close = CaseViews.iconPlain(this, R.drawable.ic_close, AppTheme.text(this), 24);
            close.setPadding(dp(10), dp(10), dp(10), dp(10));
            close.setOnClickListener(v -> closeSearch());
            topBar.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
            searchBox.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) searchBox.post(() -> imm.showSoftInput(searchBox, 0));
            return;
        }

        TextView title = CaseViews.text(this, pageTitle(), 18, AppTheme.text(this), true);
        title.setSingleLine(true);
        title.setPadding(dp(6), 0, dp(6), 0);
        topBar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView search = CaseViews.iconPlain(this, R.drawable.ic_tab_search, AppTheme.text(this), 26);
        search.setPadding(dp(8), dp(8), dp(8), dp(8));
        search.setContentDescription("Search");
        search.setOnClickListener(v -> openSearch());
        topBar.addView(search, new LinearLayout.LayoutParams(dp(44), dp(44)));
    }

    private String pageTitle() {
        if (page == CATEGORY) return CAT_NAMES[curCat];
        if (page == FOLDER && curDir != null) {
            return curDir.equals(folderRoot) ? (downloadTab ? "Download" : "Storage") : curDir.getName();
        }
        return "";
    }

    private void buildTabs() {
        tabBar.removeAllViews();
        tabBar.addView(tab(R.drawable.ic_tab_download, "Download", downloadTab, () -> {
            downloadTab = true;
            page = FOLDER;
            folderRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            curDir = folderRoot;
            closeSearchQuiet();
            render();
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        tabBar.addView(tab(R.drawable.ic_folder, "Files", !downloadTab, () -> {
            downloadTab = false;
            page = HOME;
            closeSearchQuiet();
            render();
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

    private View tab(int icon, String label, boolean on, Runnable action) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        int col = on ? AppTheme.text(this) : AppTheme.sub(this);
        ImageView iv = CaseViews.iconPlain(this, icon, col, 26);
        t.addView(iv);
        t.addView(CaseViews.text(this, label, 13, col, on));
        t.setOnClickListener(v -> action.run());
        return t;
    }

    // ------------------------------------------------------------------ pages

    private void render() {
        buildTop();
        buildTabs();
        content.removeAllViews();
        if (page == HOME) content.addView(homeView());
        else if (page == CATEGORY) showList(cats.get(curCat), null);
        else if (page == FOLDER) showList(listDir(curDir), curDir == null ? null : curDir.getAbsolutePath());
        else renderSearch(searchBox == null ? "" : searchBox.getText().toString());
        refreshPickBar();
    }

    private View homeView() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(10), dp(14), dp(20));
        sv.addView(col);

        if (!hasAccess()) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(16), dp(16), dp(16));
            card.setBackground(CaseViews.box(this, AppTheme.card(this), 12, AppTheme.cardStroke(this), 1));
            card.addView(CaseViews.text(this, "Allow access to all files", 16, AppTheme.text(this), true));
            card.addView(CaseViews.text(this, "To show your music, videos, photos and documents, turn on "
                    + "\"Allow access to all files\" for this app, then come back here.", 14, AppTheme.sub(this), false));
            TextView go = CaseViews.text(this, "Allow", 15, AppTheme.isDark(this) ? 0xFF121212 : 0xFFFFFFFF, true);
            go.setGravity(Gravity.CENTER);
            go.setPadding(0, dp(12), 0, dp(12));
            go.setBackground(CaseViews.box(this, AppTheme.accent(this), 10, 0, 0));
            go.setOnClickListener(v -> new PermissionHelper(this).request());
            card.addView(go, CaseViews.lp(this, 12, 0));
            col.addView(card);
            return sv;
        }

        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int k = 0; k < 4; k++) {
                final int idx = r * 4 + k;
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(0, dp(12), 0, dp(12));
                cell.addView(CaseViews.iconPlain(this, CAT_ICONS[idx], AppTheme.text(this), 38));
                TextView name = CaseViews.text(this, CAT_NAMES[idx], 15, AppTheme.text(this), false);
                name.setPadding(0, dp(8), 0, 0);
                cell.addView(name);
                cell.addView(CaseViews.text(this, loaded ? String.valueOf(cats.get(idx).size()) : "...",
                        13, AppTheme.sub(this), false));
                cell.setOnClickListener(v -> {
                    curCat = idx;
                    page = CATEGORY;
                    render();
                });
                row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            col.addView(row, CaseViews.lp(this, 6, 0));
        }

        col.addView(storageBar(), CaseViews.lp(this, 18, 0));

        View line = new View(this);
        line.setBackgroundColor(AppTheme.cardStroke(this));
        col.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        ((LinearLayout.LayoutParams) line.getLayoutParams()).topMargin = dp(16);

        boolean dark = AppTheme.isDark(this);
        LinearLayout clean = new LinearLayout(this);
        clean.setOrientation(LinearLayout.HORIZONTAL);
        clean.setGravity(Gravity.CENTER);
        clean.setPadding(dp(16), dp(16), dp(16), dp(16));
        clean.setBackground(CaseViews.box(this, dark ? 0x33FF9800 : 0xFFFCF4E4, 14, 0, 0));
        int orange = dark ? 0xFFFFB74D : 0xFFC2610C;
        clean.addView(CaseViews.iconPlain(this, R.drawable.ic_cat_clean, orange, 28));
        TextView ct = CaseViews.text(this, "Clean Files", 18, orange, false);
        ct.setPadding(dp(10), 0, 0, 0);
        clean.addView(ct);
        clean.setOnClickListener(v -> Toast.makeText(this, "Clean Files is coming soon", Toast.LENGTH_SHORT).show());
        col.addView(clean, CaseViews.lp(this, 16, 0));
        return sv;
    }

    private View storageBar() {
        long total = 0, avail = 0;
        try {
            StatFs sf = new StatFs(Environment.getExternalStorageDirectory().getPath());
            total = sf.getTotalBytes();
            avail = sf.getAvailableBytes();
        } catch (Exception ignored) { }
        float used = total > 0 ? Math.max(0f, Math.min(1f, (total - avail) / (float) total)) : 0f;

        FrameLayout bar = new FrameLayout(this);
        boolean dark = AppTheme.isDark(this);
        bar.setBackground(CaseViews.box(this, dark ? 0xFF2A2C3A : 0xFFEFEFEF, 10, 0, 0));
        bar.setClipToOutline(true);
        LinearLayout fills = new LinearLayout(this);
        fills.setOrientation(LinearLayout.HORIZONTAL);
        View fill = new View(this);
        fill.setBackgroundColor(dark ? 0xFF2F6B3F : 0xFFB3FFC0);
        fills.addView(fill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, Math.max(used, 0.0001f)));
        fills.addView(new View(this), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1f - used, 0.0001f)));
        bar.addView(fills, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        String txt = String.format(Locale.US, "Available: %.2fGB/Total: %.2fGB  \u203A",
                avail / 1073741824.0, total / 1073741824.0);
        TextView t = CaseViews.text(this, txt, 18, AppTheme.text(this), true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(14), dp(8), dp(14));
        bar.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bar.setOnClickListener(v -> {
            downloadTab = false;
            page = FOLDER;
            folderRoot = Environment.getExternalStorageDirectory();
            curDir = folderRoot;
            render();
        });
        return bar;
    }

    // ------------------------------------------------------------------ lists

    private List<Entry> listDir(File dir) {
        List<Entry> out = new ArrayList<>();
        File[] kids = dir == null ? null : dir.listFiles();
        if (kids == null) return out;
        for (File f : kids) {
            if (f.getName().startsWith(".")) continue;
            Entry e = new Entry();
            e.path = f.getAbsolutePath();
            e.name = f.getName();
            e.dir = f.isDirectory();
            e.size = f.length();
            e.modified = f.lastModified();
            e.cat = e.dir ? 7 : classify(e.name, null);
            out.add(e);
        }
        Collections.sort(out, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) {
                if (a.dir != b.dir) return a.dir ? -1 : 1;
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        return out;
    }

    private void showList(List<Entry> list, String subtitle) {
        shownList = list;
        content.removeAllViews();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(2), dp(16), dp(6));
        String info = subtitle != null ? subtitle
                : list.size() + (list.size() == 1 ? " file" : " files");
        TextView sub = CaseViews.text(this, info, 13, AppTheme.sub(this), false);
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.START);
        head.addView(sub, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (pick && hasFiles(list)) {
            TextView all = CaseViews.text(this, "Select all", 14, AppTheme.accent(this), true);
            all.setPadding(dp(10), dp(6), 0, dp(6));
            all.setOnClickListener(v -> {
                boolean allOn = true;
                for (Entry e : shownList) if (!e.dir && !picked.contains(e.path)) { allOn = false; break; }
                for (Entry e : shownList) {
                    if (e.dir) continue;
                    if (allOn) picked.remove(e.path); else picked.add(e.path);
                }
                refreshPickBar();
                notifyList();
            });
            head.addView(all);
        }
        box.addView(head);

        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setFastScrollEnabled(true);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return shownList.size(); }
            @Override public Object getItem(int i) { return shownList.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup parent) { return row(shownList.get(i), v); }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((p, v, i, id) -> onRowTap(shownList.get(i)));
        box.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (list.isEmpty()) {
            TextView none = CaseViews.text(this, loaded || page == FOLDER ? "Nothing here" : "Loading...",
                    15, AppTheme.sub(this), false);
            none.setGravity(Gravity.CENTER);
            none.setPadding(0, dp(40), 0, 0);
            box.addView(none, 1);
        }
        content.addView(box);
    }

    private boolean hasFiles(List<Entry> list) {
        for (Entry e : list) if (!e.dir) return true;
        return false;
    }

    private void notifyList() { if (adapter != null) adapter.notifyDataSetChanged(); }

    private View row(Entry e, View reuse) {
        LinearLayout r;
        ImageView icon;
        TextView name, sub;
        CheckBox cb;
        if (reuse == null) {
            r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(dp(16), dp(10), dp(12), dp(10));
            icon = new ImageView(this);
            r.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(14), 0, dp(8), 0);
            name = CaseViews.text(this, "", 16, AppTheme.text(this), false);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            sub = CaseViews.text(this, "", 12, AppTheme.sub(this), false);
            col.addView(name);
            col.addView(sub);
            r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            cb = new CheckBox(this);
            cb.setClickable(false);
            cb.setFocusable(false);
            r.addView(cb);
            r.setTag(new Object[]{icon, name, sub, cb});
        } else {
            r = (LinearLayout) reuse;
        }
        Object[] h = (Object[]) r.getTag();
        icon = (ImageView) h[0];
        name = (TextView) h[1];
        sub = (TextView) h[2];
        cb = (CheckBox) h[3];
        icon.setImageResource(e.dir ? R.drawable.ic_cat_others : CAT_ICONS[e.cat]);
        icon.setColorFilter(AppTheme.text(this), android.graphics.PorterDuff.Mode.SRC_IN);
        name.setText(e.name);
        if (e.dir) {
            String[] kids = new File(e.path).list();
            int n = kids == null ? 0 : kids.length;
            sub.setText(n + (n == 1 ? " item" : " items"));
        } else {
            sub.setText(fmt.format(new Date(e.modified)) + "  \u2022  " + sizeText(e.size));
        }
        cb.setVisibility(pick && !e.dir ? View.VISIBLE : View.GONE);
        cb.setChecked(picked.contains(e.path));
        return r;
    }

    private void onRowTap(Entry e) {
        if (e.dir) {
            curDir = new File(e.path);
            page = FOLDER;
            if (searching) closeSearchQuiet();
            render();
            return;
        }
        if (pick) {
            if (!picked.remove(e.path)) picked.add(e.path);
            refreshPickBar();
            notifyList();
            return;
        }
        openFile(e);
    }

    private void openFile(Entry e) {
        try {
            File f = new File(e.path);
            Uri u = PhotoFlow.uri(this, f);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext(e.name));
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(u, mime == null ? "*/*" : mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ search

    private void openSearch() {
        if (!hasAccess()) return;
        pageBeforeSearch = page;
        searching = true;
        page = SEARCH;
        render();
    }

    private void closeSearchQuiet() {
        searching = false;
        searchBox = null;
        if (page == SEARCH) page = pageBeforeSearch;
    }

    private void closeSearch() {
        closeSearchQuiet();
        render();
    }

    private void renderSearch(String q) {
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        if (!needle.isEmpty()) {
            for (Entry e : everything) {
                if (e.name.toLowerCase(Locale.ROOT).contains(needle)) {
                    out.add(e);
                    if (out.size() >= 500) break;
                }
            }
        }
        showList(out, needle.isEmpty() ? "Type to search all files" : out.size() + " found");
        refreshPickBar();
    }

    // ------------------------------------------------------------------ pick bar and results

    private void refreshPickBar() {
        if (!pick || picked.isEmpty()) {
            pickBar.setVisibility(View.GONE);
            return;
        }
        pickBar.setVisibility(View.VISIBLE);
        pickCount.setText(picked.size() + (picked.size() == 1 ? " file selected" : " files selected"));
    }

    private void finishPick() {
        if (picked.isEmpty()) return;
        Intent data = new Intent();
        data.putStringArrayListExtra(EXTRA_PATHS, new ArrayList<>(picked));
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (searching) {
            closeSearch();
            return;
        }
        if (page == FOLDER && curDir != null && folderRoot != null && !curDir.equals(folderRoot)) {
            File up = curDir.getParentFile();
            curDir = up == null ? folderRoot : up;
            render();
            return;
        }
        if (page != HOME) {
            downloadTab = false;
            page = HOME;
            render();
            return;
        }
        super.onBackPressed();
    }
}
