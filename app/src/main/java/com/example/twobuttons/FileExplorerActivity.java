package com.example.twobuttons;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The app's own file explorer for the data folder (PdfFinder): breadcrumb path bar, folders first,
 * sort by name / date / size / type, search in the folder, new folder, rename, delete, share, details.
 * It never leaves the data folder.
 */
public class FileExplorerActivity extends BaseActivity {

    private static final String[] SORTS = {"Name", "Date modified", "Size", "Type"};

    private File root;
    private File cur;
    private boolean limited;                 // all-files access missing: showing the app's private folder
    private int sort = 0;
    private boolean asc = true;
    private String query = "";

    private final List<File> shown = new ArrayList<>();
    private final Map<File, Integer> childCount = new HashMap<>();
    private LinearLayout crumbs;
    private HorizontalScrollView crumbScroll;
    private TextView summary, empty, sortLabel;
    private BaseAdapter adapter;
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Data folder");
        root = DataFolder.root();
        if (root == null) {
            limited = true;
            root = getExternalFilesDir(null);
            if (root == null) root = getFilesDir();
        }
        cur = root;
        if (savedInstanceState != null) {
            String p = savedInstanceState.getString("cur");
            if (p != null && inRoot(new File(p)) && new File(p).isDirectory()) cur = new File(p);
        }

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(AppTheme.bg(this));
        int p = CaseViews.dp(this, 12);
        page.setPadding(p, p, p, 0);

        // top bar: Up | title | sort | new folder
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        ImageView up = CaseViews.icon(this, R.drawable.ic_arrow_back, AppTheme.accent(this), 24);
        up.setContentDescription("Up one folder");
        up.setPadding(CaseViews.dp(this, 6), CaseViews.dp(this, 6), CaseViews.dp(this, 6), CaseViews.dp(this, 6));
        up.setOnClickListener(v -> goUp());
        bar.addView(up, new LinearLayout.LayoutParams(CaseViews.dp(this, 40), CaseViews.dp(this, 40)));
        TextView title = CaseViews.text(this, "Data folder", 18, AppTheme.text(this), true);
        title.setPadding(CaseViews.dp(this, 6), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout sortChip = chip(R.drawable.ic_filter, "Name");
        sortLabel = (TextView) sortChip.getChildAt(1);
        sortChip.setOnClickListener(v -> pickSort());
        bar.addView(sortChip);
        ImageView add = CaseViews.icon(this, R.drawable.ic_add_circle, AppTheme.accent(this), 28);
        add.setContentDescription("New folder");
        add.setOnClickListener(v -> newFolder());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(CaseViews.dp(this, 40), CaseViews.dp(this, 40));
        alp.leftMargin = CaseViews.dp(this, 6);
        bar.addView(add, alp);
        page.addView(bar);

        // breadcrumb path bar
        crumbs = new LinearLayout(this);
        crumbs.setOrientation(LinearLayout.HORIZONTAL);
        crumbs.setGravity(Gravity.CENTER_VERTICAL);
        crumbs.setPadding(CaseViews.dp(this, 12), CaseViews.dp(this, 8), CaseViews.dp(this, 12), CaseViews.dp(this, 8));
        crumbScroll = new HorizontalScrollView(this);
        crumbScroll.setHorizontalScrollBarEnabled(false);
        crumbScroll.setBackground(CaseViews.box(this, AppTheme.card(this), 12, AppTheme.cardStroke(this), 1));
        crumbScroll.addView(crumbs);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = CaseViews.dp(this, 6);
        page.addView(crumbScroll, clp);

        if (limited) {
            TextView note = CaseViews.text(this, "All-files access is not granted, so this shows the app's private"
                    + " folder only. Grant \"All files access\" to see PdfFinder on internal storage.",
                    12, LegalUi.RED, false);
            note.setPadding(CaseViews.dp(this, 4), CaseViews.dp(this, 6), CaseViews.dp(this, 4), 0);
            page.addView(note);
        }

        EditText search = new RoundEdit(this);
        FieldStyle.hint(search, "Search in this folder");
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                reload(false);
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.topMargin = CaseViews.dp(this, 8);
        page.addView(search, slp);

        summary = CaseViews.text(this, "", 12, AppTheme.sub(this), false);
        summary.setPadding(CaseViews.dp(this, 4), CaseViews.dp(this, 8), 0, CaseViews.dp(this, 6));
        page.addView(summary);

        adapter = new RowAdapter();
        ListView list = new ListView(this);
        list.setAdapter(adapter);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, CaseViews.dp(this, 16));
        list.setOnItemClickListener((parent, view, pos, id) -> open(shown.get(pos)));
        list.setOnItemLongClickListener((parent, view, pos, id) -> {
            options(shown.get(pos));
            return true;
        });

        empty = CaseViews.text(this, "This folder is empty", 14, AppTheme.sub(this), false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, CaseViews.dp(this, 40), 0, 0);
        android.widget.FrameLayout body = new android.widget.FrameLayout(this);
        body.addView(list, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        body.addView(empty, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("cur", cur.getAbsolutePath());
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload(false);
    }

    @Override
    public void onBackPressed() {
        if (!cur.equals(root)) goUp();
        else super.onBackPressed();
    }

    // ------------------------------------------------------------------ navigation

    private boolean inRoot(File f) {
        try {
            String r = root.getCanonicalPath();
            String c = f.getCanonicalPath();
            return c.equals(r) || c.startsWith(r + File.separator);
        } catch (Exception e) {
            return false;
        }
    }

    private void goUp() {
        if (cur.equals(root)) {
            finish();
            return;
        }
        File parent = cur.getParentFile();
        enter(parent != null && inRoot(parent) ? parent : root);
    }

    private void enter(File dir) {
        cur = dir;
        reload(true);
    }

    private void drawCrumbs() {
        crumbs.removeAllViews();
        List<File> chain = new ArrayList<>();
        for (File f = cur; f != null; f = f.getParentFile()) {
            chain.add(0, f);
            if (f.equals(root)) break;
        }
        for (int i = 0; i < chain.size(); i++) {
            final File f = chain.get(i);
            boolean last = i == chain.size() - 1;
            String label = f.equals(root) ? DataFolder.ROOT_NAME : f.getName();
            TextView t = CaseViews.text(this, label, 14, last ? AppTheme.text(this) : AppTheme.accent(this), true);
            t.setPadding(CaseViews.dp(this, 4), CaseViews.dp(this, 4), CaseViews.dp(this, 4), CaseViews.dp(this, 4));
            if (!last) t.setOnClickListener(v -> enter(f));
            crumbs.addView(t);
            if (!last) {
                TextView sep = CaseViews.text(this, "\u203A", 16, AppTheme.sub(this), false);
                sep.setPadding(CaseViews.dp(this, 4), 0, CaseViews.dp(this, 4), 0);
                crumbs.addView(sep);
            }
        }
        crumbScroll.post(() -> crumbScroll.fullScroll(View.FOCUS_RIGHT));
    }

    // ------------------------------------------------------------------ listing

    private void reload(boolean clearSearch) {
        if (!cur.isDirectory()) cur = root;
        File[] kids = cur.listFiles();
        shown.clear();
        childCount.clear();
        long bytes = 0;
        int folders = 0, files = 0;
        if (kids != null) {
            for (File k : kids) {
                if (k.isDirectory()) {
                    folders++;
                    String[] in = k.list();
                    childCount.put(k, in == null ? 0 : in.length);
                } else {
                    files++;
                    bytes += k.length();
                }
                if (!query.isEmpty() && !k.getName().toLowerCase(Locale.ROOT).contains(query)) continue;
                shown.add(k);
            }
        }
        Collections.sort(shown, comparator());
        drawCrumbs();
        sortLabel.setText(SORTS[sort] + (asc ? " \u2191" : " \u2193"));
        summary.setText(folders + (folders == 1 ? " folder" : " folders") + ", " + files
                + (files == 1 ? " file" : " files") + "  \u2022  " + size(bytes)
                + (query.isEmpty() ? "" : "  \u2022  " + shown.size() + " match"));
        empty.setText(query.isEmpty() ? "This folder is empty" : "Nothing matches your search");
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private Comparator<File> comparator() {
        return (a, b) -> {
            boolean da = a.isDirectory(), db = b.isDirectory();
            if (da != db) return da ? -1 : 1;                     // folders always first
            int r;
            switch (sort) {
                case 1: r = Long.compare(a.lastModified(), b.lastModified()); break;
                case 2: r = Long.compare(da ? 0 : a.length(), db ? 0 : b.length()); break;
                case 3: r = ext(a).compareToIgnoreCase(ext(b)); break;
                default: r = 0;
            }
            if (r == 0) r = a.getName().compareToIgnoreCase(b.getName());
            return asc ? r : -r;
        };
    }

    private void pickSort() {
        PickerDialogs.optionSheet(this, "Sort by", "Choose the same one again to reverse the order",
                Arrays.asList(SORTS), sort, which -> {
                    if (which == sort) asc = !asc;
                    else {
                        sort = which;
                        asc = which == 0 || which == 3;           // dates and sizes: newest / biggest first
                    }
                    reload(false);
                });
    }

    // ------------------------------------------------------------------ actions

    private void open(File f) {
        if (f.isDirectory()) {
            enter(f);
            return;
        }
        String e = ext(f).toLowerCase(Locale.ROOT);
        if (e.equals("pdf")) {
            Intent i = new Intent(this, MarkActivity.class);
            i.putExtra("path", f.getAbsolutePath());
            startActivity(i);
            return;
        }
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(e);
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(PhotoFlow.uri(this, f), mime == null ? "*/*" : mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(this, "No app can open this file", Toast.LENGTH_SHORT).show();
        }
    }

    private void options(final File f) {
        List<String> items = new ArrayList<>();
        items.add("Open");
        items.add("Rename");
        items.add("Delete");
        if (f.isFile()) items.add("Share");
        items.add("Details");
        PickerDialogs.optionSheet(this, f.getName(), null, items, -1, which -> {
            String s = items.get(which);
            if (s.equals("Open")) open(f);
            else if (s.equals("Rename")) rename(f);
            else if (s.equals("Delete")) delete(f);
            else if (s.equals("Share")) share(f);
            else details(f);
        });
    }

    private EditText nameBox(String value) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setText(value);
        e.setSelection(value.length());
        return e;
    }

    private static boolean badName(String n) {
        return n.isEmpty() || n.equals(".") || n.equals("..") || n.contains("/") || n.contains("\\");
    }

    private void newFolder() {
        final EditText e = nameBox("");
        e.setHint("Folder name");
        AlertDialog d = new AlertDialog.Builder(this).setTitle("New folder").setView(pad(e))
                .setPositiveButton("Create", null).setNegativeButton("Cancel", null).create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = e.getText().toString().trim();
            File nf = new File(cur, n);
            if (badName(n)) Toast.makeText(this, "Enter a valid name", Toast.LENGTH_SHORT).show();
            else if (nf.exists()) Toast.makeText(this, "\"" + n + "\" already exists", Toast.LENGTH_SHORT).show();
            else if (!nf.mkdirs()) Toast.makeText(this, "Could not create the folder", Toast.LENGTH_SHORT).show();
            else {
                d.dismiss();
                reload(false);
            }
        });
    }

    private void rename(final File f) {
        final EditText e = nameBox(f.getName());
        AlertDialog d = new AlertDialog.Builder(this).setTitle("Rename").setView(pad(e))
                .setPositiveButton("Rename", null).setNegativeButton("Cancel", null).create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = e.getText().toString().trim();
            File nf = new File(f.getParentFile(), n);
            if (badName(n)) Toast.makeText(this, "Enter a valid name", Toast.LENGTH_SHORT).show();
            else if (nf.exists() && !nf.equals(f)) Toast.makeText(this, "\"" + n + "\" already exists", Toast.LENGTH_SHORT).show();
            else if (!f.renameTo(nf)) Toast.makeText(this, "Could not rename", Toast.LENGTH_SHORT).show();
            else {
                if (f.isFile()) new MarkStore(this).move(this, f.getAbsolutePath(), nf.getAbsolutePath());
                d.dismiss();
                reload(false);
            }
        });
    }

    private void delete(final File f) {
        String what = f.isDirectory() ? "The folder \"" + f.getName() + "\" and everything inside it"
                : "\"" + f.getName() + "\"";
        PickerDialogs.confirm(this, "Delete?", what + " will be deleted permanently. It cannot be undone.",
                "Delete", () -> {
                    boolean dir = f.isDirectory();
                    String path = f.getAbsolutePath();
                    boolean ok = deleteTree(f);
                    if (ok && !dir) new MarkStore(this).clear(path);
                    Toast.makeText(this, ok ? "Deleted" : "Could not delete everything", Toast.LENGTH_SHORT).show();
                    reload(false);
                });
    }

    private static boolean deleteTree(File f) {
        boolean ok = true;
        File[] kids = f.isDirectory() ? f.listFiles() : null;
        if (kids != null) for (File k : kids) ok &= deleteTree(k);
        return f.delete() && ok;
    }

    private void share(File f) {
        try {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext(f).toLowerCase(Locale.ROOT));
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(mime == null ? "*/*" : mime);
            i.putExtra(Intent.EXTRA_STREAM, PhotoFlow.uri(this, f));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Share"));
        } catch (Exception ex) {
            Toast.makeText(this, "Cannot share this file", Toast.LENGTH_SHORT).show();
        }
    }

    private void details(File f) {
        StringBuilder sb = new StringBuilder();
        sb.append("Name: ").append(f.getName()).append("\n\n");
        sb.append("Location: ").append(f.getParent()).append("\n\n");
        sb.append("Type: ").append(f.isDirectory() ? "Folder" : kind(f)).append("\n\n");
        if (f.isDirectory()) {
            int[] n = new int[2];
            long b = tally(f, n);
            sb.append("Contains: ").append(n[0]).append(n[0] == 1 ? " file, " : " files, ")
                    .append(n[1]).append(n[1] == 1 ? " folder" : " folders").append("\n\n");
            sb.append("Size: ").append(size(b)).append("\n\n");
        } else {
            sb.append("Size: ").append(size(f.length())).append(" (").append(f.length()).append(" bytes)\n\n");
        }
        sb.append("Modified: ").append(fmt.format(new Date(f.lastModified())));
        TextView t = new TextView(this);
        t.setText(sb.toString());
        t.setTextSize(14);
        t.setTextIsSelectable(true);
        t.setTextColor(AppTheme.text(this));
        t.setPadding(CaseViews.dp(this, 20), CaseViews.dp(this, 12), CaseViews.dp(this, 20), 0);
        new AlertDialog.Builder(this).setTitle("Details").setView(t).setPositiveButton("Close", null).show();
    }

    private static long tally(File dir, int[] n) {
        long b = 0;
        File[] kids = dir.listFiles();
        if (kids == null) return 0;
        for (File k : kids) {
            if (k.isDirectory()) { n[1]++; b += tally(k, n); }
            else { n[0]++; b += k.length(); }
        }
        return b;
    }

    // ------------------------------------------------------------------ look

    private View pad(View v) {
        LinearLayout box = new LinearLayout(this);
        box.setPadding(CaseViews.dp(this, 20), CaseViews.dp(this, 8), CaseViews.dp(this, 20), 0);
        box.addView(v, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private LinearLayout chip(int icon, String label) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(CaseViews.dp(this, 10), CaseViews.dp(this, 6), CaseViews.dp(this, 12), CaseViews.dp(this, 6));
        b.setBackground(CaseViews.box(this, AppTheme.field(this), 20, AppTheme.accent(this), 1));
        b.addView(CaseViews.icon(this, icon, AppTheme.accent(this), 16));
        TextView t = CaseViews.text(this, label, 12, AppTheme.accent(this), true);
        t.setPadding(CaseViews.dp(this, 5), 0, 0, 0);
        b.addView(t);
        return b;
    }

    private static String ext(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return f.isDirectory() || dot < 0 || dot == n.length() - 1 ? "" : n.substring(dot + 1);
    }

    private static String kind(File f) {
        String e = ext(f).toLowerCase(Locale.ROOT);
        switch (e) {
            case "pdf": return "PDF document";
            case "doc": case "docx": return "Word document";
            case "jpg": case "jpeg": case "png": case "webp": case "gif": return "Image (" + e.toUpperCase(Locale.ROOT) + ")";
            case "json": return "JSON data";
            case "zip": return "Zip archive";
            case "": return "File";
            default: return e.toUpperCase(Locale.ROOT) + " file";
        }
    }

    private int iconFor(File f) {
        if (f.isDirectory()) return R.drawable.ic_folder;
        String e = ext(f).toLowerCase(Locale.ROOT);
        if (e.equals("pdf")) return R.drawable.ic_pdf;
        if (e.equals("doc") || e.equals("docx")) return R.drawable.ic_word;
        if (e.equals("jpg") || e.equals("jpeg") || e.equals("png") || e.equals("webp") || e.equals("gif"))
            return R.drawable.ic_image;
        return R.drawable.ic_notes;
    }

    private int colorFor(File f) {
        if (f.isDirectory()) return 0xFFF5A800;
        String e = ext(f).toLowerCase(Locale.ROOT);
        if (e.equals("pdf")) return 0xFFD93025;
        if (e.equals("doc") || e.equals("docx")) return 0xFF1A73E8;
        if (e.equals("jpg") || e.equals("jpeg") || e.equals("png") || e.equals("webp") || e.equals("gif"))
            return 0xFF1E8E3E;
        return 0xFF6B7280;
    }

    static String size(long b) {
        if (b < 1024) return b + " B";
        double v = b / 1024.0;
        if (v < 1024) return String.format(Locale.US, "%.1f KB", v);
        v /= 1024;
        if (v < 1024) return String.format(Locale.US, "%.1f MB", v);
        return String.format(Locale.US, "%.2f GB", v / 1024);
    }

    private class RowAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            final File f = shown.get(i);
            final FileExplorerActivity c = FileExplorerActivity.this;
            LinearLayout outer = new LinearLayout(c);
            outer.setPadding(0, CaseViews.dp(c, 4), 0, CaseViews.dp(c, 4));

            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(CaseViews.dp(c, 12), CaseViews.dp(c, 10), CaseViews.dp(c, 12), CaseViews.dp(c, 10));
            row.setBackground(CaseViews.box(c, AppTheme.card(c), 14, AppTheme.cardStroke(c), 1));

            ImageView ic = CaseViews.icon(c, iconFor(f), colorFor(f), 26);
            row.addView(ic, new LinearLayout.LayoutParams(CaseViews.dp(c, 36), CaseViews.dp(c, 36)));

            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(CaseViews.dp(c, 10), 0, CaseViews.dp(c, 6), 0);
            TextView name = CaseViews.text(c, f.getName(), 15, AppTheme.text(c), true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            col.addView(name);
            String sub;
            if (f.isDirectory()) {
                Integer n = childCount.get(f);
                int k = n == null ? 0 : n;
                sub = k + (k == 1 ? " item" : " items") + "  \u2022  " + fmt.format(new Date(f.lastModified()));
            } else {
                sub = size(f.length()) + "  \u2022  " + fmt.format(new Date(f.lastModified()));
            }
            col.addView(CaseViews.text(c, sub, 12, AppTheme.sub(c), false));
            row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            if (f.isDirectory()) {
                row.addView(CaseViews.icon(c, R.drawable.ic_chevron_right, AppTheme.sub(c), 20),
                        new LinearLayout.LayoutParams(CaseViews.dp(c, 24), CaseViews.dp(c, 24)));
            }
            outer.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            return outer;
        }
    }
}
