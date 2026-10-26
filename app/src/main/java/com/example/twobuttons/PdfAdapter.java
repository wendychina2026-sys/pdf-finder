package com.example.twobuttons;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.File;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class PdfAdapter extends ArrayAdapter<Pdf> {

    private static final java.text.SimpleDateFormat DATE_FMT =
            new java.text.SimpleDateFormat("dd MMM yyyy", Locale.US);

    final Set<String> selected = new LinkedHashSet<>();
    boolean selectionMode = false;
    String query = "";
    /** -1 = all kinds, else Pdf.PDF / Pdf.IMAGE / Pdf.VIDEO */
    int kindFilter = -1;
    /** 0 = name A-Z, 1 = size small to large, 2 = size large to small, 3 = date newest, 4 = date oldest */
    int sortMode = 0;
    /** Date filter on last modified (epoch millis, inclusive). 0 = no limit. Applies to PDFs, images and videos. */
    long dateFrom = 0, dateTo = 0;

    private static final int THUMB_PX = 160;
    private final LruCache<String, Bitmap> thumbs = new LruCache<>(150);
    private final ExecutorService loader = Executors.newFixedThreadPool(2);
    private final Handler ui = new Handler(Looper.getMainLooper());

    PdfAdapter(Context c) {
        super(c, 0, new ArrayList<Pdf>());
    }

    // Rebuild visible list from master list + search text. Returns visible count.
    int filter(List<Pdf> all, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        this.query = q;
        clear();
        Set<String> visible = new HashSet<>();
        List<Pdf> keep = new ArrayList<>();
        for (Pdf p : all) {
            if (kindFilter >= 0 && p.kind != kindFilter) continue;
            if (dateFrom > 0 && p.modified < dateFrom) continue;
            if (dateTo > 0 && p.modified > dateTo) continue;
            if (q.isEmpty() || p.name.toLowerCase(Locale.ROOT).contains(q)) {
                keep.add(p);
                visible.add(p.path);
            }
        }
        if (sortMode == 1) {
            Collections.sort(keep, (a, b) -> a.size != b.size ? Long.compare(a.size, b.size)
                    : a.name.compareToIgnoreCase(b.name));
        } else if (sortMode == 2) {
            Collections.sort(keep, (a, b) -> a.size != b.size ? Long.compare(b.size, a.size)
                    : a.name.compareToIgnoreCase(b.name));
        } else if (sortMode == 3) {
            Collections.sort(keep, (a, b) -> a.modified != b.modified ? Long.compare(b.modified, a.modified)
                    : a.name.compareToIgnoreCase(b.name));
        } else if (sortMode == 4) {
            Collections.sort(keep, (a, b) -> a.modified != b.modified ? Long.compare(a.modified, b.modified)
                    : a.name.compareToIgnoreCase(b.name));
        } else {
            Collections.sort(keep, (a, b) -> a.name.compareToIgnoreCase(b.name));
        }
        for (Pdf p : keep) add(p);
        selected.retainAll(visible); // hidden files never stay selected
        return getCount();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Row r = (convertView == null) ? new Row(getContext()) : (Row) convertView;
        Pdf p = getItem(position);
        r.name.setText(Highlight.apply(p.name, query));
        r.path.setText(Highlight.apply(p.path, query));
        r.size.setText(p.modified > 0
                ? Pdf.formatSize(p.size) + "  \u2022  " + DATE_FMT.format(new java.util.Date(p.modified))
                : Pdf.formatSize(p.size));
        bindThumb(r.thumb, p);
        boolean isSelected = selected.contains(p.path);
        r.box.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        r.box.setChecked(isSelected);
        r.setBackgroundColor(isSelected ? 0x2233AAFF : Color.TRANSPARENT);
        return r;
    }

    // Small picture: real thumbnail for images and videos, an icon for PDFs (loaded off the UI thread).
    private void bindThumb(final ImageView iv, final Pdf p) {
        iv.setTag(p.path);
        Bitmap cached = thumbs.get(p.path);
        if (cached != null) {
            iv.clearColorFilter();
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(cached);
            return;
        }
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setColorFilter(AppTheme.sub(getContext()));
        iv.setImageResource(p.kind == Pdf.PDF ? R.drawable.ic_pdf
                : p.kind == Pdf.IMAGE ? R.drawable.ic_image : R.drawable.ic_camera);
        if (p.kind == Pdf.PDF) return;
        loader.execute(() -> {
            final Bitmap b = p.kind == Pdf.IMAGE ? imageThumb(p.path) : videoThumb(p.path);
            if (b == null) return;
            thumbs.put(p.path, b);
            ui.post(() -> {
                if (p.path.equals(iv.getTag())) {          // row not recycled for another file meanwhile
                    iv.clearColorFilter();
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setImageBitmap(b);
                }
            });
        });
    }

    private static Bitmap imageThumb(String path) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int s = 1;
            while (o.outWidth / (s * 2) >= THUMB_PX && o.outHeight / (s * 2) >= THUMB_PX) s *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = s;
            return BitmapFactory.decodeFile(path, d);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap videoThumb(String path) {
        MediaMetadataRetriever m = new MediaMetadataRetriever();
        try {
            m.setDataSource(path);
            Bitmap b = m.getFrameAtTime(-1);
            if (b == null) return null;
            int w = b.getWidth(), h = b.getHeight(), big = Math.max(w, h);
            if (big > THUMB_PX * 2) {
                float f = (THUMB_PX * 2f) / big;
                b = Bitmap.createScaledBitmap(b, Math.max(1, (int) (w * f)), Math.max(1, (int) (h * f)), true);
            }
            return b;
        } catch (Throwable t) {
            return null;
        } finally {
            try { m.release(); } catch (Throwable ignored) { }
        }
    }

    // Row: [checkbox] [thumbnail] name (bold) / path (small grey)
    static class Row extends LinearLayout {
        final CheckBox box;
        final ImageView thumb;
        final TextView name;
        final TextView path;
        final TextView size;

        Row(Context c) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(16, 20, 16, 20);

            box = new CheckBox(c);
            box.setFocusable(false);
            box.setClickable(false);

            thumb = new ImageView(c);
            thumb.setClipToOutline(true);
            thumb.setBackground(CaseViews.box(c, AppTheme.card(c), 8, AppTheme.cardStroke(c), 1));

            LinearLayout col = new LinearLayout(c);
            col.setOrientation(VERTICAL);
            name = new TextView(c);
            name.setTextSize(16);
            name.setTypeface(null, Typeface.BOLD);
            name.setTextColor(AppTheme.text(c));
            path = new TextView(c);
            path.setTextSize(12);
            path.setTextColor(Color.GRAY);
            col.addView(name);
            col.addView(path);

            size = new TextView(c);
            size.setTextSize(12);
            size.setTextColor(AppTheme.sub(c));
            size.setPadding(16, 0, 0, 0);

            addView(box, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            int tp = CaseViews.dp(c, 48);
            LayoutParams tlp = new LayoutParams(tp, tp);
            tlp.leftMargin = CaseViews.dp(c, 8);
            addView(thumb, tlp);
            LayoutParams clp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            clp.leftMargin = CaseViews.dp(c, 10);
            addView(col, clp);
            addView(size, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
    }
}
