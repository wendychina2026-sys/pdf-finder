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
            Intent i = new Intent(this, MarkActivity.class);
            i.putExtra("path", items.get(pos).path);
            startActivity(i);
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
        for (String p : paths) {
            Item n = new Item(p, everything.get(p));
            File f = new File(p);
            n.total = totalPages(p);
            n.size = android.text.format.Formatter.formatShortFileSize(this, f.length());
            n.date = df.format(new java.util.Date(f.lastModified()));
            out.add(n);
        }
        return out;
    }

    private void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        items.clear();
        for (Item it : all) {
            if (q.isEmpty() || matches(it, q)) items.add(it);
        }
        status.setText(items.size() + " marked PDFs");
        adapter.notifyDataSetChanged();
    }

    private boolean matches(Item it, String q) {
        if (new File(it.path).getName().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (it.path.toLowerCase(Locale.ROOT).contains(q)) return true;
        for (String sub : it.marks.values()) {
            if (sub.toLowerCase(Locale.ROOT).contains(q)) return true;
        }
        return false;
    }

    private int totalPages(String path) {
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
    private void showFileOptions(final Item it) {
        PickerDialogs.optionSheet(this, new File(it.path).getName(), null,
                java.util.Arrays.asList("Delete file", "Reset marking"), -1, which -> {
                    if (which == 0) {
                        PickerDialogs.confirm(this, "Delete file?",
                                "\"" + new File(it.path).getName() + "\" and all its page marks will be deleted from your phone. It cannot be undone.",
                                "Delete", () -> {
                                    boolean ok = new File(it.path).delete();
                                    store.clear(it.path);
                                    if (ok) android.media.MediaScannerConnection.scanFile(
                                            this, new String[]{it.path}, null, null);
                                    android.widget.Toast.makeText(this, ok ? "File deleted"
                                            : "Marks cleared, but the file could not be deleted",
                                            android.widget.Toast.LENGTH_LONG).show();
                                    loadAll();
                                    applyFilter();
                                });
                    } else {
                        PickerDialogs.confirm(this, "Reset marking?",
                                "All " + it.marks.size() + " page marks of \"" + new File(it.path).getName()
                                        + "\" will be removed. The file is kept.",
                                "Reset", () -> {
                                    store.clear(it.path);
                                    loadAll();
                                    applyFilter();
                                });
                    }
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
                Intent i = new Intent(this, MarkActivity.class);
                i.putExtra("path", it.path);
                i.putExtra("add_cat", subName);
                startActivity(i);
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
                Intent i = new Intent(this, MarkActivity.class);
                i.putExtra("path", it.path);
                i.putExtra("only_sub", subName);
                startActivity(i);
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

    /** The complete path of a marked PDF, on request (the card itself stays short). */
    private void showPath(Item it) {
        TextView t = new TextView(this);
        t.setText(it.path);
        t.setTextSize(14);
        t.setTextIsSelectable(true);
        t.setTextColor(AppTheme.text(this));
        t.setPadding(48, 24, 48, 0);
        new AlertDialog.Builder(this)
                .setTitle("File path")
                .setView(t)
                .setPositiveButton("Close", null)
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
            info.name = f.getName();
            info.fileName = f.getName();
            java.util.Set<String> cats = new TreeSet<>(it.marks.values());
            info.category = cats.size() == 1 ? cats.iterator().next() : "Multiple";
            String m = getSharedPreferences("pdf_matters", MODE_PRIVATE).getString(it.path, "");
            info.matter = (m.isEmpty() || m.equals(MatterPicker.VAULT)) ? "General Document Vault" : m;
            info.size = it.size;
            info.date = it.date;
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

            DocFileCard.Actions act = new DocFileCard.Actions();
            act.open = () -> {
                Intent in = new Intent(c, MarkActivity.class);
                in.putExtra("path", it.path);
                startActivity(in);
            };
            act.details = () -> showDetails(it);
            act.path = () -> showPath(it);
            act.share = () -> {
                try {
                    android.net.Uri u = androidx.core.content.FileProvider.getUriForFile(
                            c, getPackageName() + ".fileprovider", f);
                    DocFileCard.share(c, u, f.getName());
                } catch (Exception e) {
                    android.widget.Toast.makeText(c, "Cannot share this file", android.widget.Toast.LENGTH_SHORT).show();
                }
            };
            act.delete = () -> showFileOptions(it);
            LinearLayout card = DocFileCard.build(c, info, act);
            longPressAll(card, it);
            card.setOnLongClickListener(v -> { showFileOptions(it); return true; });

            outer.addView(card, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            return outer;
        }
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
