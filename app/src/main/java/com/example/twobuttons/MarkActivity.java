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
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Arrays;
import java.util.Map;

public class MarkActivity extends Activity {

    private String path;
    private ParcelFileDescriptor pfd;
    private PdfRenderer renderer;
    private final LruCache<Integer, Bitmap> cache = new LruCache<>(4);
    private MarkStore store;
    private Map<Integer, String> marks;
    private PageAdapter pageAdapter;

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
        Button categories = new Button(this);
        categories.setText("Categories");
        categories.setOnClickListener(v ->
                startActivity(new Intent(this, CategoryActivity.class)));
        top.addView(name, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(categories);

        TextView hint = new TextView(this);
        hint.setText("Long-press a page to mark it");
        hint.setTextSize(12);
        hint.setTextColor(Color.GRAY);

        pageAdapter = new PageAdapter();
        ListView pages = new ListView(this);
        pages.setAdapter(pageAdapter);
        pages.setOnItemLongClickListener((parent, view, pos, id) -> {
            showMarkDialog(pos);
            return true;
        });

        LinearLayout.LayoutParams wide = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        root.addView(top, wide);
        root.addView(hint, wide);
        root.addView(pages, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
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

    // New mark, or view/edit existing mark.
    private void showMarkDialog(int page) {
        String current = marks.get(page);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 24, 48, 0);
        TextView msg = new TextView(this);
        msg.setText("Mark page " + (page + 1) + " as:");
        Spinner spin = new Spinner(this);
        spin.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, MarkStore.SUBS));
        if (current != null) {
            spin.setSelection(Math.max(Arrays.asList(MarkStore.SUBS).indexOf(current), 0));
        }
        box.addView(msg);
        box.addView(spin);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(current == null ? "Mark page" : "Page mark")
                .setView(box)
                .setPositiveButton("OK", (d, w) -> {
                    String sub = (String) spin.getSelectedItem();
                    store.set(path, page, sub);
                    marks.put(page, sub);
                    pageAdapter.notifyDataSetChanged();
                })
                .setNegativeButton("Cancel", null);
        if (current != null) {
            b.setNeutralButton("Remove mark", (d, w) -> {
                store.remove(path, page);
                marks.remove(page);
                pageAdapter.notifyDataSetChanged();
            });
        }
        b.show();
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
        @Override public int getCount() { return renderer.getPageCount(); }
        @Override public Object getItem(int i) { return i; }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            PageRow r = (convertView == null)
                    ? new PageRow(MarkActivity.this) : (PageRow) convertView;
            String sub = marks.get(i);
            r.label.setText("Page " + (i + 1) + " / " + getCount());
            r.image.setImageBitmap(render(i));
            if (sub != null) {
                r.tag.setText("Mark as " + sub);
                r.tag.setVisibility(View.VISIBLE);
                r.badge.setVisibility(View.VISIBLE);
            } else {
                r.tag.setVisibility(View.GONE);
                r.badge.setVisibility(View.GONE);
            }
            r.tag.setOnClickListener(v -> showMarkDialog(i));
            r.badge.setOnClickListener(v -> showMarkDialog(i));
            return r;
        }
    }
}
