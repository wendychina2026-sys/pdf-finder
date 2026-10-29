package com.example.twobuttons;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

public class MarkedFilesActivity extends BaseActivity {

    @Override
    protected int navIndex() { return 2; }

    static class Item {
        final String path;
        final Map<Integer, String> marks;
        int total = -1;
        String size = "", date = "";
        String refName = "", stage = "";    // the name the user gave the document when uploading it
        LegalStore.Doc doc;                 // the uploaded-document record behind this file (null for plain marked PDFs)
        boolean grouped;                    // one of several files on a single card
        String label = "";                  // the name shown for this file inside a multi-file card
        final List<Item> extras = new ArrayList<>();   // further files of the same document group: shown on this ONE card

        Item(String path, Map<Integer, String> marks) {
            this.path = path;
            this.marks = marks;
        }
    }

    private final List<Item> all = new ArrayList<>();
    private final List<Item> items = new ArrayList<>();
    private BaseAdapter adapter;
    private TextView status;
    private EditText search;
    private MarkStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Document Vault");
        store = new MarkStore(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);

        search = new RoundEdit(this);
        FieldStyle.hint(search, "Search file name, path or category");
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { applyFilter(); }
        });

        status = new TextView(this);
        status.setPadding(0, 8, 0, 12);

        adapter = new ItemAdapter();
        ListView list = new ListView(this);
        list.setAdapter(adapter);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setOnItemClickListener((parent, view, pos, id) -> {
            openMarker(items.get(pos).path, null, null);
        });

        list.setOnItemLongClickListener((parent, view, pos, id) -> {
            showFileOptions(items.get(pos));
            return true;
        });

        Button addCase = LegalUi.outlineButton(this, "+  Add Case");
        addCase.setOnClickListener(v -> startActivity(new Intent(this, NewCaseActivity.class)));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        addLp.bottomMargin = CaseViews.dp(this, 8);
        root.addView(addCase, addLp);
        root.addView(search, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, CaseViews.dp(this, 84));   // keep the last rows clear of the + button

        TextView fab = new TextView(this);
        fab.setText("+");
        fab.setTextSize(30);
        fab.setTextColor(0xFFFFFFFF);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(CaseViews.box(this, AppTheme.accent(this), 30, 0, 0));
        fab.setContentDescription("Add case");
        fab.setOnClickListener(v -> startActivity(new Intent(this, NewCaseActivity.class)));

        FrameLayout frame = new FrameLayout(this);
        frame.addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(
                CaseViews.dp(this, 60), CaseViews.dp(this, 60), Gravity.BOTTOM | Gravity.END);
        fl.setMargins(0, 0, CaseViews.dp(this, 20), CaseViews.dp(this, 24));
        frame.addView(fab, fl);
        setContentView(frame);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        // File.exists() per marked PDF is disk work; keep it off the UI thread so the tab opens instantly.
        new Thread(() -> {
            final List<Item> loaded = readAll();
            runOnUiThread(() -> {
                if (isFinishing()) return;
                all.clear();
                all.addAll(loaded);
                applyFilter();
            });
        }).start();
    }

    private void loadAll() {
        all.clear();
        all.addAll(readAll());
    }

    private List<Item> readAll() {
        List<Item> out = new ArrayList<>();
        Map<String, Map<Integer, String>> everything = store.everything();
        List<String> paths = new ArrayList<>();
        for (String p : everything.keySet()) {
            Map<Integer, String> m = everything.get(p);
            if (new File(p).exists() && m != null && !m.isEmpty()) paths.add(p);
        }
        Collections.sort(paths, (a, b) ->
                new File(a).getName().compareToIgnoreCase(new File(b).getName()));
        java.text.DateFormat df = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM);
        // uploaded documents: the stored file's URI -> the document record (it holds the reference name)
        Map<String, LegalStore.Doc> byUri = new java.util.HashMap<>();
        for (LegalStore.Doc d : new LegalStore(this).docs()) {
            if (!d.uri.isEmpty() && !byUri.containsKey(d.uri)) byUri.put(d.uri, d);
            for (String u : d.more) if (!byUri.containsKey(u)) byUri.put(u, d);
        }
        for (String p : paths) {
            Item n = new Item(p, everything.get(p));
            File f = new File(p);
            n.total = totalPages(p);
            try {
                LegalStore.Doc d = byUri.get(PhotoFlow.uri(this, f).toString());
                if (d != null) { n.refName = d.name; n.stage = d.stage; n.doc = d; }
            } catch (Exception ignored) { }
            n.size = android.text.format.Formatter.formatShortFileSize(this, f.length());
            n.date = df.format(new java.util.Date(f.lastModified()));
            out.add(n);
        }
        // files saved together in one document (Add to an existing document) are ONE card, not one card per file
        Map<String, Item> lead = new java.util.HashMap<>();
        List<Item> merged = new ArrayList<>();
        for (Item n : out) {
            if (n.doc == null || n.doc.more.isEmpty()) { merged.add(n); continue; }
            Item head = lead.get(n.doc.id);
            if (head == null) { lead.put(n.doc.id, n); merged.add(n); continue; }
            boolean nIsFirst = false;
            try { nIsFirst = PhotoFlow.uri(this, new File(n.path)).toString().equals(n.doc.uri); } catch (Exception ignored) { }
            if (nIsFirst) {                       // the document's own first file leads its card
                n.extras.add(head);
                n.extras.addAll(head.extras);
                head.extras.clear();
                lead.put(n.doc.id, n);
                merged.set(merged.indexOf(head), n);
            } else {
                head.extras.add(n);
            }
        }
        for (Item h : merged) {
            if (h.extras.isEmpty()) continue;
            h.grouped = true;
            for (Item x : h.extras) x.grouped = true;
        }
        return merged;
    }

    private void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        items.clear();
        for (Item it : all) {
            if (q.isEmpty() || matches(it, q)) items.add(it);
        }
        status.setText(items.size() + " marked files");
        adapter.notifyDataSetChanged();
    }

    private boolean matches(Item it, String q) {
        if (new File(it.path).getName().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (it.refName.toLowerCase(Locale.ROOT).contains(q)) return true;
        if (it.path.toLowerCase(Locale.ROOT).contains(q)) return true;
        if (FileCaption.get(this, it.path).toLowerCase(Locale.ROOT).contains(q)) return true;
        for (Item x : it.extras) {
            if (FileCaption.get(this, x.path).toLowerCase(Locale.ROOT).contains(q)) return true;
            if (new File(x.path).getName().toLowerCase(Locale.ROOT).contains(q)) return true;
        }
        for (String sub : it.marks.values()) {
            if (sub.toLowerCase(Locale.ROOT).contains(q)) return true;
        }
        return false;
    }

    /** PDFs open in the Category marker; an image, Word file (or any other file) opens in the phone's viewer. */
    private void openMarker(String path, String onlySub, String addCat) {
        if (path.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            Intent i = new Intent(this, MarkActivity.class);
            i.putExtra("path", path);
            if (onlySub != null) i.putExtra("only_sub", onlySub);
            if (addCat != null) i.putExtra("add_cat", addCat);
            startActivity(i);
            return;
        }
        try {
            File f = new File(path);
            android.net.Uri u = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", f);
            String ext = DocFileCard.ext(f.getName());
            String mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            Intent v = new Intent(Intent.ACTION_VIEW);
            v.setDataAndType(u, mime == null ? "*/*" : mime);
            v.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(v);
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "No app found to open this file", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private int totalPages(String path) {
        int fk = DocFileCard.kind(new File(path).getName());
        if (fk == 1 || fk == 2) return 1;      // a single image / Word file = one page
        try (ParcelFileDescriptor pfd =
                     ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer r = new PdfRenderer(pfd)) {
            return r.getPageCount();
        } catch (Exception e) {
            return -1;
        }
    }

    // sorted 0-based pages -> "1-3, 7, 9-10" (1-based)
    private static String ranges(List<Integer> pages) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < pages.size()) {
            int start = pages.get(i);
            int end = start;
            while (i + 1 < pages.size() && pages.get(i + 1) == end + 1) {
                end = pages.get(++i);
            }
            if (sb.length() > 0) sb.append(", ");
            if (start == end) sb.append(start + 1);
            else sb.append(start + 1).append('-').append(end + 1);
            i++;
        }
        return sb.toString();
    }

    // long-press on a PDF: delete the file, or reset all its page marks
    /** Long-press: only Reset marking. The real file is never deleted from Document Vault. */
    private void showFileOptions(final Item it) {
        PickerDialogs.confirm(this, "Reset marking?",
                "All " + it.marks.size() + " page marks of \"" + new File(it.path).getName()
                        + "\" will be removed. The file is kept.",
                "Reset", () -> {
                    store.clear(it.path);
                    loadAll();
                    applyFilter();
                });
    }

    private void showDetails(Item it) {
        int total = totalPages(it.path);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(CaseViews.dp(this, 16), CaseViews.dp(this, 8), CaseViews.dp(this, 16), CaseViews.dp(this, 8));

        // keep MarkStore.SUBS order, then any unknown names
        List<String> order = new ArrayList<>();
        Collections.addAll(order, MarkStore.SUBS);
        for (String s : new TreeSet<>(it.marks.values())) if (!order.contains(s)) order.add(s);
        // the category of the first marked page comes first, then the next one, and so on
        final Map<String, Integer> first = new java.util.HashMap<>();
        for (Map.Entry<Integer, String> e : it.marks.entrySet()) {
            Integer cur = first.get(e.getValue());
            if (cur == null || e.getKey() < cur) first.put(e.getValue(), e.getKey());
        }
        Collections.sort(order, (x, y) -> Integer.compare(
                first.containsKey(x) ? first.get(x) : Integer.MAX_VALUE,
                first.containsKey(y) ? first.get(y) : Integer.MAX_VALUE));

        final AlertDialog[] holder = new AlertDialog[1];

        for (String sub : order) {
            final List<Integer> pages = new ArrayList<>();
            for (Map.Entry<Integer, String> e : it.marks.entrySet()) {
                if (sub.equals(e.getValue())) pages.add(e.getKey());
            }
            if (pages.isEmpty()) continue;
            Collections.sort(pages);
            final String subName = sub;
            final String rangeText = ranges(pages);

            // one round card per marked category: name, pages, View / Delete, and + to add more pages to it
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(CaseViews.dp(this, 10), CaseViews.dp(this, 7), CaseViews.dp(this, 10), CaseViews.dp(this, 8));
            card.setBackground(CaseViews.box(this, AppTheme.card(this), 12, AppTheme.cardStroke(this), 1));

            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = CaseViews.text(this, sub, 14, AppTheme.text(this), true);
            head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            ImageView add = CaseViews.icon(this, R.drawable.ic_add_circle, AppTheme.accent(this), 26);
            add.setContentDescription("Add pages to " + sub);
            add.setOnClickListener(v -> {
                openMarker(it.path, null, subName);
                if (holder[0] != null) holder[0].dismiss();
            });
            head.addView(add, new LinearLayout.LayoutParams(CaseViews.dp(this, 30), CaseViews.dp(this, 30)));
            card.addView(head);

            TextView info = CaseViews.text(this, "Range: " + (pages.size() == 1 ? "page " : "pages ") + rangeText
                    + "  (" + pages.size() + (pages.size() == 1 ? " page)" : " pages)"),
                    12, AppTheme.sub(this), false);
            info.setPadding(0, CaseViews.dp(this, 1), 0, CaseViews.dp(this, 6));
            card.addView(info);

            LinearLayout btns = new LinearLayout(this);
            btns.setOrientation(LinearLayout.HORIZONTAL);
            btns.addView(chipButton("View", R.drawable.ic_eye, AppTheme.accent(this), () -> {
                openMarker(it.path, subName, null);
                if (holder[0] != null) holder[0].dismiss();
            }));
            LinearLayout delBtn = chipButton("Delete", R.drawable.ic_delete, LegalUi.RED, () ->
                    new AlertDialog.Builder(this)
                            .setTitle("Unmark " + subName + "?")
                            .setMessage("Remove " + subName + " mark from pages " + rangeText + "?")
                            .setPositiveButton("Delete", (d, w) -> {
                                for (int pg : pages) store.remove(it.path, pg);
                                if (holder[0] != null) holder[0].dismiss();
                                loadAll();
                                applyFilter();
                                for (Item n : all) {
                                    if (n.path.equals(it.path)) {
                                        showDetails(n);
                                        break;
                                    }
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show());
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            dlp.leftMargin = CaseViews.dp(this, 8);
            btns.addView(delBtn, dlp);
            card.addView(btns);

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.topMargin = CaseViews.dp(this, 6);
            box.addView(card, clp);
        }

        int marked = it.marks.size();
        StringBuilder sb = new StringBuilder();
        sb.append("Marked pages: ").append(marked).append('\n');
        if (total >= 0) {
            sb.append("Unmarked pages: ").append(Math.max(0, total - marked)).append('\n');
            sb.append("Total pages: ").append(total);
        } else {
            sb.append("Total pages: unknown (cannot read PDF)");
        }
        TextView sum = new TextView(this);
        sum.setText(sb.toString());
        sum.setTextSize(13);
        sum.setTextColor(AppTheme.text(MarkedFilesActivity.this));
        sum.setPadding(0, 14, 0, 0);
        box.addView(sum);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);

        holder[0] = new AlertDialog.Builder(this)
                .setTitle(new File(it.path).getName())
                .setView(sv)
                .setPositiveButton("Close", null)
                .show();
    }

    /** Small round outlined button: icon + label. */
    private LinearLayout chipButton(String label, int iconRes, int color, Runnable onClick) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(CaseViews.dp(this, 10), CaseViews.dp(this, 4), CaseViews.dp(this, 12), CaseViews.dp(this, 4));
        b.setBackground(CaseViews.box(this, AppTheme.field(this), 20, color, 1));
        b.addView(CaseViews.icon(this, iconRes, color, 16));
        TextView t = CaseViews.text(this, label, 12, color, true);
        t.setPadding(CaseViews.dp(this, 6), 0, 0, 0);
        b.addView(t);
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    /** Renames the file as the user sees it (one file inside a multi-file card); the file on disk is untouched. */
    private void editFileName(final Item g, String current) {
        LinearLayout form = LegalUi.form(this);
        final EditText et = LegalUi.text(this, form, "File name / caption", current);
        LegalUi.formDialog(this, "Edit file name", form, () -> {
            if (!LegalUi.need(this, et, "File name")) return false;
            FileCaption.set(this, g.path, LegalUi.str(et));
            reload();
            return true;
        }, null);
    }

    private void shareFile(String path) {
        try {
            File f = new File(path);
            android.net.Uri u = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", f);
            DocFileCard.share(this, u, f.getName());
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Cannot share this file", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /** "1600 x 1200 px" for an image, else "". */
    private static String imageSize(File f) {
        try {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            return o.outWidth > 0 ? o.outWidth + " \u00D7 " + o.outHeight + " px" : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** The small line under a file's name in a multi-file card: what tells the files apart. */
    private String fileMeta(Item g, int kind) {
        StringBuilder b = new StringBuilder(DocFileCard.kindLabel(kind, new File(g.path).getName()));
        if (kind == 2) {
            String d = imageSize(new File(g.path));
            if (!d.isEmpty()) b.append("  \u2022  ").append(d);
        }
        if (!g.date.isEmpty()) b.append("  \u2022  ").append(g.date);
        return b.toString();
    }

    /** The (i) dialog: type, size, dates, marked pages and the complete path, plus a way to reach the folder. */
    private void showPath(final Item it) {
        final File f = new File(it.path);
        final int pad = CaseViews.dp(this, 16);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, CaseViews.dp(this, 8), pad, CaseViews.dp(this, 4));

        int k = DocFileCard.kind(f.getName());
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(CaseViews.icon(this, DocFileCard.kindIcon(k), DocFileCard.kindColor(this, k), 30));
        TextView nm = CaseViews.text(this, it.grouped ? (it.label.isEmpty() ? f.getName() : it.label) : (it.refName.isEmpty() ? f.getName() : it.refName), 15, AppTheme.text(this), true);
        nm.setPadding(CaseViews.dp(this, 10), 0, 0, 0);
        head.addView(nm, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(head);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(CaseViews.dp(this, 12), CaseViews.dp(this, 6), CaseViews.dp(this, 12), CaseViews.dp(this, 6));
        card.setBackground(CaseViews.box(this, AppTheme.field(this), 14, AppTheme.fieldStroke(this), 1));
        java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT);
        if (!it.refName.isEmpty() && !it.grouped) infoRow(card, "Document name", it.refName);
        infoRow(card, "File name", f.getName());
        if (it.grouped) infoRow(card, "Part of", it.refName.isEmpty() ? "this document" : it.refName);
        infoRow(card, "Type", DocFileCard.kindLabel(k, f.getName()));
        if (k == 2) {
            String dim = imageSize(f);
            if (!dim.isEmpty()) infoRow(card, "Dimensions", dim);
        }
        infoRow(card, "Size", it.size.isEmpty() ? "-" : it.size);
        infoRow(card, "Last edited", df.format(new java.util.Date(f.lastModified())));
        int n = it.marks.size();
        if (k == 0) infoRow(card, "Marked pages", n + (it.total >= 0 ? " of " + it.total : ""));
        infoRow(card, "Location", f.getParent() == null ? "" : f.getParent());
        String from = DataFolder.originOf(this, f);
        infoRow(card, "Moved from", from.isEmpty() ? "Not recorded (added before this was tracked, or created in the app)" : from);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = CaseViews.dp(this, 10);
        box.addView(card, clp);

        TextView pl = CaseViews.text(this, "Full path", 11, AppTheme.sub(this), true);
        pl.setPadding(0, CaseViews.dp(this, 10), 0, CaseViews.dp(this, 2));
        box.addView(pl);
        TextView pt = CaseViews.text(this, it.path, 12, AppTheme.text(this), false);
        pt.setTextIsSelectable(true);
        box.addView(pt);

        final AlertDialog[] dlg = new AlertDialog[1];
        LinearLayout btns = new LinearLayout(this);
        btns.setPadding(0, CaseViews.dp(this, 12), 0, 0);
        btns.addView(chipButton("Open folder", R.drawable.ic_folder, AppTheme.accent(this), () -> {
            if (dlg[0] != null) dlg[0].dismiss();
            openContainingFolder(f);
        }));
        LinearLayout copy = chipButton("Copy path", R.drawable.ic_notes, AppTheme.sub(this), () -> {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("path", it.path));
            android.widget.Toast.makeText(this, "Path copied", android.widget.Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.leftMargin = CaseViews.dp(this, 8);
        btns.addView(copy, cp);
        box.addView(btns);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        dlg[0] = new AlertDialog.Builder(this)
                .setTitle("File information")
                .setView(sv)
                .setPositiveButton("Close", null)
                .show();
    }

    private void infoRow(LinearLayout parent, String label, String value) {
        LinearLayout r = new LinearLayout(this);
        r.setPadding(0, CaseViews.dp(this, 5), 0, CaseViews.dp(this, 5));
        r.addView(CaseViews.text(this, label, 12, AppTheme.sub(this), true),
                new LinearLayout.LayoutParams(CaseViews.dp(this, 96), LinearLayout.LayoutParams.WRAP_CONTENT));
        r.addView(CaseViews.text(this, value, 13, AppTheme.text(this), false),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(r);
    }

    /** The app's own explorer when the file is inside the data folder, else the system file manager. */
    private void openContainingFolder(File f) {
        File dir = f.getParentFile();
        if (dir == null) return;
        File root = DataFolder.root();
        try {
            if (root != null && dir.getCanonicalPath().startsWith(root.getCanonicalPath())) {
                Intent i = new Intent(this, FileExplorerActivity.class);
                i.putExtra("dir", dir.getCanonicalPath());
                i.putExtra("focus", f.getCanonicalPath());
                startActivity(i);
                return;
            }
        } catch (java.io.IOException ignored) { }
        String prim = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/";
        String abs = dir.getAbsolutePath() + "/";
        try {
            if (abs.startsWith(prim)) {
                String rel = abs.substring(prim.length());
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(android.net.Uri.parse(
                        "content://com.android.externalstorage.documents/document/primary%3A"
                                + android.net.Uri.encode(rel.endsWith("/") ? rel.substring(0, rel.length() - 1) : rel)),
                        "vnd.android.document/directory");
                startActivity(i);
                return;
            }
        } catch (Exception ignored) { }
        android.widget.Toast.makeText(this, "Cannot open this folder: " + dir.getAbsolutePath(),
                android.widget.Toast.LENGTH_LONG).show();
    }

    /** The case this document is attached to (null for the General Document Vault or a case that is gone). */
    private DiaryStore.HCase caseFor(Item it) {
        String m = getSharedPreferences("pdf_matters", MODE_PRIVATE).getString(it.path, "");
        if (m.isEmpty() || m.equals(MatterPicker.VAULT) || m.equals("General Document Vault")) return null;
        for (DiaryStore.HCase hc : new DiaryStore(this).cases()) if (hc.title().equals(m)) return hc;
        return null;
    }

    // Three separate notes: the attached case's Notes (shared with New Case), the card's own note,
    // and one note per file row of a multi-file card.

    private String caseNote(Item it) {
        DiaryStore.HCase hc = caseFor(it);
        return hc == null || hc.notes == null ? "" : hc.notes.trim();
    }

    private String cardNote(Item it) {
        android.content.SharedPreferences sp = getSharedPreferences("doc_notes", MODE_PRIVATE);
        String v = sp.getString("card:" + it.path, null);
        if (v == null) v = sp.getString(it.path, "");      // note saved by the first version
        return v.trim();
    }

    private String fileNote(String path) {
        return getSharedPreferences("doc_notes", MODE_PRIVATE).getString("file:" + path, "").trim();
    }

    private EditText noteField(String label, String value, LinearLayout box) {
        TextView l = CaseViews.text(this, label, 12, AppTheme.sub(this), true);
        l.setPadding(0, CaseViews.dp(this, 8), 0, CaseViews.dp(this, 2));
        box.addView(l);
        EditText et = new EditText(this);
        et.setText(value);
        et.setHint("Notes");
        et.setMinLines(3);
        et.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        et.setTextColor(AppTheme.text(this));
        et.setHintTextColor(AppTheme.sub(this));
        box.addView(et, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return et;
    }

    private LinearLayout noteBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = CaseViews.dp(this, 20);
        box.setPadding(pad, CaseViews.dp(this, 4), pad, 0);
        return box;
    }

    /** Card notes icon: the card's own note, plus (when attached to a case) that case's Notes as a separate field. */
    private void editCardNotes(Item it) {
        final DiaryStore.HCase hc = caseFor(it);
        LinearLayout box = noteBox();
        final EditText caseEt = hc == null ? null
                : noteField("Case notes (same as New Case > Notes)", hc.notes, box);
        final EditText cardEt = noteField(hc == null ? "Document notes" : "Document notes (this card only)",
                cardNote(it), box);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Notes: " + (hc != null ? hc.title() : new File(it.path).getName()))
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    getSharedPreferences("doc_notes", MODE_PRIVATE).edit()
                            .putString("card:" + it.path, cardEt.getText().toString().trim()).apply();
                    if (hc != null) {
                        DiaryStore ds = new DiaryStore(this);
                        DiaryStore.HCase fresh = ds.caseById(hc.id);     // re-read: the case may have changed meanwhile
                        if (fresh == null) fresh = hc;
                        fresh.notes = caseEt.getText().toString().trim();
                        ds.saveCase(fresh);
                    }
                    adapter.notifyDataSetChanged();
                })
                .show();
    }

    /** File-row notes icon: a note for that one file only. */
    private void editFileNotes(Item g, String label) {
        LinearLayout box = noteBox();
        final EditText et = noteField("Notes for this file", fileNote(g.path), box);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Notes: " + label)
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    getSharedPreferences("doc_notes", MODE_PRIVATE).edit()
                            .putString("file:" + g.path, et.getText().toString().trim()).apply();
                    adapter.notifyDataSetChanged();
                })
                .show();
    }

    private class ItemAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            final Item it = items.get(i);
            final MarkedFilesActivity c = MarkedFilesActivity.this;
            LinearLayout outer = new LinearLayout(c);
            outer.setPadding(0, CaseViews.dp(c, 5), 0, CaseViews.dp(c, 5));

            File f = new File(it.path);
            DocFileCard.Info info = new DocFileCard.Info();
            // the title is the name the user gave the document; the real file name stays in the details
            String ext = DocFileCard.ext(f.getName());
            info.name = it.refName.isEmpty() ? f.getName()
                    : (DocFileCard.ext(it.refName).isEmpty() && !ext.isEmpty() ? it.refName + "." + ext : it.refName);
            info.fileName = f.getName();
            info.stage = it.stage;
            java.util.Set<String> cats = new TreeSet<>(it.marks.values());
            info.category = cats.size() == 1 ? cats.iterator().next() : "Multiple";
            String m = getSharedPreferences("pdf_matters", MODE_PRIVATE).getString(it.path, "");
            info.matter = (m.isEmpty() || m.equals(MatterPicker.VAULT)) ? "General Document Vault" : m;
            info.hideMeta = true;       // type, size and date are in the (i) dialog
            info.notesFilled = !cardNote(it).isEmpty() || !caseNote(it).isEmpty();
            if (!it.extras.isEmpty()) info.count = 1 + it.extras.size();
            int n = it.marks.size();
            info.pagesLine = "Marked pages: " + n
                    + (it.total >= 0 ? "  \u2022  Unmarked: " + Math.max(0, it.total - n) + "  \u2022  Total: " + it.total : "");
            StringBuilder rs = new StringBuilder();
            List<String> ord = new ArrayList<>(cats);
            final Map<String, Integer> first = new java.util.HashMap<>();
            for (Map.Entry<Integer, String> e : it.marks.entrySet()) {
                Integer cur = first.get(e.getValue());
                if (cur == null || e.getKey() < cur) first.put(e.getValue(), e.getKey());
            }
            Collections.sort(ord, (x, y) -> Integer.compare(first.get(x), first.get(y)));
            for (String cat : ord) {
                List<Integer> pg = new ArrayList<>();
                for (Map.Entry<Integer, String> e : it.marks.entrySet()) if (cat.equals(e.getValue())) pg.add(e.getKey());
                Collections.sort(pg);
                if (rs.length() > 0) rs.append("  |  ");
                rs.append(cat).append(": ").append(ranges(pg));
            }
            info.rangeLine = rs.toString();
            if (it.extras.isEmpty() && DocFileCard.kind(f.getName()) != 0) {
                info.pagesLine = "";            // an image / Word file is one page: no marked / unmarked / total
                info.rangeLine = "";
            }
            if (!it.extras.isEmpty()) {
                // one card, several files: totals over ALL files, and every file named with its own marking
                final List<Item> grp = new ArrayList<>();
                grp.add(it);
                grp.addAll(it.extras);
                int marked = 0, totalPg = 0;
                boolean totalKnown = true;
                StringBuilder names = new StringBuilder();
                boolean anyPdf = false;
                for (int gi = 0; gi < grp.size(); gi++) {
                    final Item g = grp.get(gi);
                    marked += g.marks.size();
                    if (g.total >= 0) totalPg += g.total; else totalKnown = false;
                    String gname = new File(g.path).getName();
                    int gk = DocFileCard.kind(gname);
                    if (gk == 0) anyPdf = true;
                    if (names.length() > 0) names.append("\n");
                    names.append(gi + 1).append(". ").append(gname);
                    DocFileCard.Sub sb = new DocFileCard.Sub();
                    String label = FileCaption.get(c, g.path);
                    if (label.isEmpty()) {
                        String base = DocFileCard.stem(it.refName.isEmpty() ? new File(it.path).getName() : it.refName);
                        label = base + " (" + (gi + 1) + ")";
                    }
                    g.label = label;
                    final String shownLabel = label;
                    sb.name = label;
                    sb.kind = gk;
                    sb.open = () -> openMarker(g.path, null, null);
                    sb.details = () -> showPath(g);
                    sb.edit = () -> editFileName(g, shownLabel);
                    sb.share = () -> shareFile(g.path);
                    sb.notes = () -> editFileNotes(g, shownLabel);
                    sb.notesFilled = !fileNote(g.path).isEmpty();
                    sb.meta = fileMeta(g, gk);
                    info.subs.add(sb);
                }
                // images / Word files are one page each: only PDFs get the marked / unmarked / total line
                info.pagesLine = grp.size() + " files in this document"
                        + (anyPdf ? "  \u2022  Marked pages: " + marked
                        + (totalKnown ? "  \u2022  Unmarked: " + Math.max(0, totalPg - marked)
                        + "  \u2022  Total: " + totalPg : "") : "");
                info.fileName = names.toString();
                info.rangeLine = "";
            }

            DocFileCard.Actions act = new DocFileCard.Actions();
            // a multi-file card opens each file from its own row; no card-level Open
            act.open = !it.extras.isEmpty() ? null : () -> {
                if (it.extras.isEmpty()) { openMarker(it.path, null, null); return; }
                final List<Item> grp = new ArrayList<>();
                grp.add(it);
                grp.addAll(it.extras);
                List<String> names = new ArrayList<>();
                for (Item g : grp) names.add(new File(g.path).getName());
                PickerDialogs.optionSheet(c, "Files in this document", null, names, -1,
                        which -> openMarker(grp.get(which).path, null, null));
            };
            act.details = !it.extras.isEmpty() ? null : () -> showDetails(it);
            act.edit = () -> editItem(it);
            act.notes = () -> editCardNotes(it);
            act.add = () -> addFiles(it);
            act.path = !it.extras.isEmpty() ? null : () -> showPath(it);
            // a multi-file card has Open / info / edit / share on every file row
            act.share = !it.extras.isEmpty() ? null : () -> {
                try {
                    if (!it.extras.isEmpty()) {
                        List<android.net.Uri> us = new ArrayList<>();
                        us.add(androidx.core.content.FileProvider.getUriForFile(
                                c, getPackageName() + ".fileprovider", f));
                        for (Item g : it.extras) us.add(androidx.core.content.FileProvider.getUriForFile(
                                c, getPackageName() + ".fileprovider", new File(g.path)));
                        DocFileCard.shareAll(c, us);
                        return;
                    }
                    android.net.Uri u = androidx.core.content.FileProvider.getUriForFile(
                            c, getPackageName() + ".fileprovider", f);
                    DocFileCard.share(c, u, f.getName());
                } catch (Exception e) {
                    android.widget.Toast.makeText(c, "Cannot share this file", android.widget.Toast.LENGTH_SHORT).show();
                }
            };
            LinearLayout card = DocFileCard.build(c, info, act);
            longPressAll(card, it);
            card.setOnLongClickListener(v -> { showFileOptions(it); return true; });

            outer.addView(card, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            return outer;
        }
    }

    /**
     * Edit button on a Document Vault card: Document name, Category, Stage and Attach to matter.
     * The real file (and its name on disk) is never touched. Category changes the page marks only when
     * all marked pages share one category; with several categories they are edited in Show details.
     */
    private void editItem(final Item it) {
        DocEdit.show(this, it.path, this::reload);
    }

    private static final int REQ_ADD = 72;
    private Item addTarget;

    /** Add button on a card: pick more images / PDFs / Word files; they join THIS card's document. */
    private void addFiles(Item it) {
        addTarget = it;
        Intent in = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        in.addCategory(Intent.CATEGORY_OPENABLE);
        in.setType("*/*");
        in.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf", "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "image/*"});
        in.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        in.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(in, REQ_ADD);
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "No file picker available on this device", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_ADD || res != RESULT_OK || data == null || addTarget == null) return;
        final Item it = addTarget;
        addTarget = null;
        final List<android.net.Uri> got = new ArrayList<>();
        android.content.ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) got.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            got.add(data.getData());
        }
        if (got.isEmpty()) return;
        java.util.Set<String> cats = new TreeSet<>(it.marks.values());
        CategoryPicker.pick(this, cats.size() == 1 ? cats.iterator().next() : "",
                cat -> attachFiles(it, got, cat));
    }

    /** New files are stored in the app folder, marked with the category, and listed on this card. */
    private void attachFiles(Item it, List<android.net.Uri> uris, String cat) {
        if (cat == null || cat.trim().isEmpty()) return;
        List<android.net.Uri> added = new ArrayList<>();
        for (android.net.Uri u : uris) {
            File f = DataFolder.takeIn(this, u);
            if (f == null) continue;
            try {
                android.net.Uri fu = PhotoFlow.uri(this, f);
                int k = DocFileCard.kind(f.getName());
                if (k == 3) continue;
                int pages = k == 0 ? Math.max(1, totalPages(f.getAbsolutePath())) : 1;
                for (int pg = 0; pg < pages; pg++) store.set(f.getAbsolutePath(), pg, cat.trim());
                added.add(fu);
            } catch (Exception ignored) { }
        }
        if (added.isEmpty()) {
            android.widget.Toast.makeText(this, "Could not add file", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        LegalStore ls = new LegalStore(this);
        LegalStore.Doc d = it.doc != null ? it.doc : DocEdit.docFor(this, it.path);
        File lead = new File(it.path);
        if (d == null) {
            d = new LegalStore.Doc();
            d.name = it.refName.isEmpty() ? lead.getName() : it.refName;
            d.date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(new java.util.Date());
            d.caseName = DocEdit.matterOf(this, it.path);
            d.stage = it.stage;
            java.util.Set<String> cs = new TreeSet<>(it.marks.values());
            d.category = cs.size() == 1 ? cs.iterator().next() : cat.trim();
            try { d.uri = PhotoFlow.uri(this, lead).toString(); } catch (Exception ignored) { }
        }
        for (android.net.Uri u : added) {
            String us = u.toString();
            if (!us.equals(d.uri) && !d.more.contains(us)) d.more.add(us);
        }
        List<android.net.Uri> grp = new ArrayList<>();
        if (!d.uri.isEmpty()) grp.add(android.net.Uri.parse(d.uri));
        for (String m : d.more) grp.add(android.net.Uri.parse(m));
        d.fileName = FileSetDialog.label(this, grp);
        d.size = FileSetDialog.totalSize(this, grp);
        ls.saveDoc(d);
        android.widget.Toast.makeText(this, added.size() + (added.size() == 1 ? " file added" : " files added"),
                android.widget.Toast.LENGTH_SHORT).show();
        reload();
    }

    /** Long-press anywhere on a card (its tappable parts too) offers Delete file / Reset marking. */
    private void longPressAll(View v, final Item it) {
        if (v.hasOnClickListeners()) v.setOnLongClickListener(x -> { showFileOptions(it); return true; });
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int k = 0; k < g.getChildCount(); k++) longPressAll(g.getChildAt(k), it);
        }
    }
}
