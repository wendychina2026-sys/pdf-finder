package com.example.twobuttons;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Map;

public class MarkActivity extends BaseActivity {

    private String path;
    private ParcelFileDescriptor pfd;
    private PdfRenderer renderer;
    private final LruCache<Integer, Bitmap> cache = new LruCache<>(4);
    private MarkStore store;
    private Map<Integer, String> marks;
    private PageAdapter pageAdapter;
    private String onlySub = "";
    private String addCat = "";      // category preselected by the Vault's add button
    private int[] visible = null;   // null = all pages; else real page indexes to show
    private LinearLayout infoBox;   // details strip: Document name, Attached to matter, Stage + Change details

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Category marker");
        path = getIntent().getStringExtra("path");

        try {
            pfd = ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(pfd);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open PDF", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        store = new MarkStore(this);
        marks = store.all(path);
        String o = getIntent().getStringExtra("only_sub");
        onlySub = o == null ? "" : o;
        String ac = getIntent().getStringExtra("add_cat");
        addCat = ac == null ? "" : ac;
        rebuildVisible();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView name = new TextView(this);
        name.setText(new File(path).getName());
        name.setTypeface(null, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(name, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView hint = new TextView(this);
        hint.setText(!addCat.isEmpty()
                ? "Adding pages to: " + addCat + "  (long-press a page to mark it)"
                : onlySub.isEmpty()
                ? "Long-press a page to mark it"
                : "Showing only pages marked: " + onlySub + "  (long-press to edit mark)");
        hint.setTextSize(12);
        hint.setTextColor(Color.GRAY);

        pageAdapter = new PageAdapter();
        ListView pages = new ListView(this);
        pages.setAdapter(pageAdapter);
        pages.setOnItemLongClickListener((parent, view, pos, id) -> {
            showMarkDialog(realPage(pos));
            return true;
        });

        LinearLayout.LayoutParams wide = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        root.addView(top, wide);
        // an already marked / saved file: show the details filled in Upload Legal Document and let them be changed
        infoBox = new LinearLayout(this);
        infoBox.setOrientation(LinearLayout.VERTICAL);
        if (!getIntent().getBooleanExtra("from_new_case", false)) {
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ip.topMargin = CaseViews.dp(this, 6);
            ip.bottomMargin = CaseViews.dp(this, 4);
            root.addView(infoBox, ip);
            refreshInfo();
        }
        root.addView(hint, wide);
        root.addView(pages, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    /** Details strip for a marked / saved file: Document name, Attached to matter, Stage, Change details. */
    private void refreshInfo() {
        infoBox.removeAllViews();
        LegalStore.Doc d = DocEdit.docFor(this, path);
        infoBox.setVisibility(View.VISIBLE);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = CaseViews.dp(this, 10);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(CaseViews.box(this, AppTheme.card(this), 12, AppTheme.sub(this) & 0x55FFFFFF, 1));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        String docName = d == null || d.name.isEmpty() ? new File(path).getName() : d.name;
        TextView nm = CaseViews.text(this, docName, 14, AppTheme.text(this), true);
        nm.setSingleLine(true);
        nm.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        head.addView(nm, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView chg = CaseViews.text(this, "Change details", 12, IconPalette.pick(this, 0xFF1A237E, 0xFFFFD54F), true);
        chg.setPadding(CaseViews.dp(this, 10), CaseViews.dp(this, 4), CaseViews.dp(this, 10), CaseViews.dp(this, 4));
        chg.setBackground(CaseViews.box(this, 0x00000000, 8, IconPalette.pick(this, 0xFF1A237E, 0xFFFFD54F), 1));
        chg.setOnClickListener(v -> DocEdit.show(this, path, () -> {
            marks = store.all(path);        // a category change re-marks every page
            pageAdapter.notifyDataSetChanged();
            refreshInfo();
        }, np -> path = np));       // saving moved the file into the Vault folder: follow it
        head.addView(chg);
        card.addView(head);

        java.util.Set<String> cats = new java.util.TreeSet<>(marks.values());
        String cat = cats.isEmpty() ? "" : cats.size() == 1 ? cats.iterator().next() : "Multiple";
        addRow(card, "Attached to matter", DocEdit.matterOf(this, path));
        if (d != null && !d.stage.isEmpty()) addRow(card, "Stage", d.stage);
        if (!cat.isEmpty()) addRow(card, "Category", cat);
        infoBox.addView(card);
    }

    private void addRow(LinearLayout card, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, CaseViews.dp(this, 3), 0, 0);
        row.addView(CaseViews.text(this, label + ": ", 11, AppTheme.sub(this), true));
        TextView v = CaseViews.text(this, value, 12, AppTheme.text(this), false);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(v, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(row);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (renderer != null) renderer.close();
            if (pfd != null) pfd.close();
        } catch (Exception ignored) {
        }
    }

    private void rebuildVisible() {
        if (onlySub.isEmpty()) {
            visible = null;
            return;
        }
        java.util.List<Integer> list = new java.util.ArrayList<>();
        for (Map.Entry<Integer, String> e : marks.entrySet()) {
            if (onlySub.equals(e.getValue())) list.add(e.getKey());
        }
        java.util.Collections.sort(list);
        visible = new int[list.size()];
        for (int i = 0; i < visible.length; i++) visible[i] = list.get(i);
    }

    private int realPage(int pos) {
        return visible == null ? pos : visible[pos];
    }

    // New mark, or view/edit existing mark. Uses the same Document Category picker
    // (search, add new category, drill-down) as the rest of the app.
    private void showMarkDialog(int page) {
        final String current = marks.get(page);
        final String[] chosen = {current != null ? current : addCat};

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 24, 48, 0);
        TextView msg = new TextView(this);
        msg.setText("Mark page " + (page + 1) + " as:");
        box.addView(msg);
        final TextView[] tv = new TextView[1];
        tv[0] = FieldStyle.dropdown(this, box, "Select category", v ->
                CategoryPicker.pick(this, chosen[0], s -> {
                    chosen[0] = s;
                    FieldStyle.setDropdownValue(tv[0], s, "Select category");
                }, new java.util.HashMap<>(marks)), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        FieldStyle.setDropdownValue(tv[0], chosen[0], "Select category");

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(current == null ? "Mark page" : "Page mark")
                .setView(box)
                .setPositiveButton("OK", null)
                .setNegativeButton("Cancel", null);
        if (current != null) b.setNeutralButton("Remove mark", null);
        AlertDialog dlg = b.create();
        dlg.show();
        if (current != null) {
            // Remove mark: unmark the page and reset the box, but keep the dialog open (Cancel closes it).
            Button rm = dlg.getButton(AlertDialog.BUTTON_NEUTRAL);
            rm.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Remove mark?")
                    .setMessage("Page " + (page + 1) + " will no longer be marked as \"" + current + "\".")
                    .setPositiveButton("Remove", (d, w) -> {
                        store.remove(path, page);
                        marks.remove(page);
                        pageAdapter.notifyDataSetChanged();
                        chosen[0] = "";
                        FieldStyle.setDropdownValue(tv[0], "", "Select category");
                        rm.setVisibility(View.GONE);
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
        }
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (chosen[0].isEmpty()) {
                Toast.makeText(this, "Select a category", Toast.LENGTH_SHORT).show();
                return;
            }
            if (current != null && !current.equals(chosen[0])) {
                new AlertDialog.Builder(this)
                        .setTitle("Change mark?")
                        .setMessage("Page " + (page + 1) + " is marked as \"" + current
                                + "\". Change it to \"" + chosen[0] + "\"?")
                        .setPositiveButton("Change", (d, w) -> {
                            store.set(path, page, chosen[0]);
                            marks.put(page, chosen[0]);
                            keepInFolder();
                            pageAdapter.notifyDataSetChanged();
                            dlg.dismiss();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            }
            store.set(path, page, chosen[0]);
            marks.put(page, chosen[0]);
            keepInFolder();
            pageAdapter.notifyDataSetChanged();
            dlg.dismiss();
        });
    }

    /**
     * Marking a page no longer moves the PDF. It moves into the app folder (PdfFinder/Documents) when the user
     * saves it in the Vault or attaches it to a matter (NewCaseActivity), and its marks follow.
     */
    private void keepInFolder() {
        // marking alone does not move the file: it moves when it is saved in the Vault / attached to a matter
    }

    private Bitmap render(int index) {
        Bitmap b = cache.get(index);
        if (b != null) return b;
        PdfRenderer.Page page = renderer.openPage(index);
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = (int) ((float) w * page.getHeight() / page.getWidth());
        b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.eraseColor(Color.WHITE);
        page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        page.close();
        cache.put(index, b);
        return b;
    }

    // Row: label / page image with "Mark as X" tag top-left and star top-right
    static class PageRow extends LinearLayout {
        final TextView label;
        final ImageView image;
        final ImageView badge;
        final TextView tag;

        PageRow(Context c) {
            super(c);
            setOrientation(VERTICAL);
            setPadding(0, 8, 0, 8);

            label = new TextView(c);
            label.setTypeface(null, Typeface.BOLD);

            image = new ImageView(c);
            image.setAdjustViewBounds(true);

            badge = new ImageView(c);
            badge.setImageResource(android.R.drawable.star_big_on);
            int size = (int) (44 * c.getResources().getDisplayMetrics().density);
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                    size, size, Gravity.TOP | Gravity.END);
            bp.setMargins(0, 8, 8, 0);

            tag = new TextView(c);
            tag.setTextColor(Color.WHITE);
            tag.setTypeface(null, Typeface.BOLD);
            tag.setTextSize(14);
            tag.setBackgroundColor(0xCC2E7D32);
            tag.setPadding(16, 8, 16, 8);
            FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.START);
            tp.setMargins(8, 8, 0, 0);

            FrameLayout frame = new FrameLayout(c);
            frame.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            frame.addView(tag, tp);
            frame.addView(badge, bp);

            addView(label);
            addView(frame, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private class PageAdapter extends BaseAdapter {
        @Override public int getCount() {
            return visible == null ? renderer.getPageCount() : visible.length;
        }
        @Override public void notifyDataSetChanged() {
            rebuildVisible();
            super.notifyDataSetChanged();
        }
        @Override public Object getItem(int i) { return i; }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            PageRow r = (convertView == null)
                    ? new PageRow(MarkActivity.this) : (PageRow) convertView;
            final int pg = realPage(i);
            String sub = marks.get(pg);
            r.label.setText("Page " + (pg + 1) + " / " + renderer.getPageCount());
            r.image.setImageBitmap(render(pg));
            if (sub != null) {
                r.tag.setText("Mark as " + sub);
                r.tag.setVisibility(View.VISIBLE);
                r.badge.setVisibility(View.VISIBLE);
            } else {
                r.tag.setVisibility(View.GONE);
                r.badge.setVisibility(View.GONE);
            }
            r.tag.setOnClickListener(v -> showMarkDialog(pg));
            r.badge.setOnClickListener(v -> showMarkDialog(pg));
            return r;
        }
    }
}
