package com.example.twobuttons;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Open a PDF again: delete pages you do not need, add camera photos as new pages, save. */
final class PdfPageEditor {
    interface Saved {
        void done(Uri neu);
    }

    private PdfPageEditor() {}

    static void open(Activity a, PhotoFlow flow, Uri pdf, Saved cb) {
        final int[] cnt = {0};
        try (ParcelFileDescriptor pfd = a.getContentResolver().openFileDescriptor(pdf, "r");
             PdfRenderer r = new PdfRenderer(pfd)) {
            cnt[0] = r.getPageCount();
        } catch (Exception e) {
            Toast.makeText(a, "Cannot open this PDF to edit", Toast.LENGTH_LONG).show();
            return;
        }
        final int count = cnt[0];
        if (count == 0) {
            Toast.makeText(a, "This PDF has no pages", Toast.LENGTH_LONG).show();
            return;
        }
        final boolean[] removed = new boolean[count];
        final List<File> added = new ArrayList<>();
        final Bitmap[] thumbs = new Bitmap[count];
        final ImageView[] ivs = new ImageView[count];
        final Map<File, Bitmap> addThumbs = new HashMap<>();

        LinearLayout outer = new LinearLayout(a);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(CaseViews.dp(a, 16), CaseViews.dp(a, 4), CaseViews.dp(a, 16), 0);
        TextView hint = CaseViews.text(a, "Delete pages you do not need, or add camera photos as new pages at the end.",
                12, AppTheme.sub(a), false);
        hint.setPadding(0, 0, 0, CaseViews.dp(a, 6));
        outer.addView(hint);
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        outer.addView(box);
        ScrollView sv = new ScrollView(a);
        sv.addView(outer);

        final AlertDialog dlg = new AlertDialog.Builder(a)
                .setTitle("Edit PDF pages")
                .setView(sv)
                .setCancelable(false)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Add photos", null)
                .create();

        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            box.removeAllViews();
            int left = added.size();
            for (boolean b : removed) if (!b) left++;
            final boolean last = left <= 1;
            int k = 0;
            for (int i = 0; i < count; i++) {
                ivs[i] = null;
                if (removed[i]) continue;
                k++;
                final int idx = i;
                ImageView iv = thumb(a);
                if (thumbs[i] != null) iv.setImageBitmap(thumbs[i]);
                ivs[i] = iv;
                box.addView(row(a, iv, "Page " + k, v -> {
                    if (last) {
                        Toast.makeText(a, "Keep at least one page", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    removed[idx] = true;
                    fill[0].run();
                }));
            }
            for (final File f : new ArrayList<>(added)) {
                ImageView iv = thumb(a);
                Bitmap b = addThumbs.get(f);
                if (b == null) {
                    try {
                        b = PdfTools.decode(f, 240);
                        if (b != null) addThumbs.put(f, b);
                    } catch (Exception ignored) {
                        // no preview
                    }
                }
                if (b != null) iv.setImageBitmap(b);
                box.addView(row(a, iv, "New photo", v -> {
                    if (last) {
                        Toast.makeText(a, "Keep at least one page", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    added.remove(f);
                    f.delete();
                    addThumbs.remove(f);
                    fill[0].run();
                }));
            }
        };
        fill[0].run();
        dlg.show();

        // page previews, rendered off the main thread
        new Thread(() -> {
            try (ParcelFileDescriptor pfd = a.getContentResolver().openFileDescriptor(pdf, "r");
                 PdfRenderer r = new PdfRenderer(pfd)) {
                for (int i = 0; i < count; i++) {
                    PdfRenderer.Page p = r.openPage(i);
                    int w = 240;
                    int h = Math.max(1, w * p.getHeight() / Math.max(1, p.getWidth()));
                    Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    b.eraseColor(Color.WHITE);
                    p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    p.close();
                    thumbs[i] = b;
                    final int k = i;
                    a.runOnUiThread(() -> {
                        if (ivs[k] != null) ivs[k].setImageBitmap(b);
                    });
                }
            } catch (Exception ignored) {
                // previews are optional
            }
        }).start();

        dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                flow.start(false, new PhotoFlow.Result() {
                    @Override public void images(List<File> files) {
                        added.addAll(files);
                        fill[0].run();
                    }
                    @Override public void pdf(File file) {}
                }));
        dlg.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
            for (File f : added) f.delete();
            dlg.dismiss();
        });
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            boolean changed = !added.isEmpty();
            for (boolean b : removed) changed |= b;
            if (!changed) {
                dlg.dismiss();
                return;
            }
            v.setEnabled(false);
            Toast.makeText(a, "Saving PDF...", Toast.LENGTH_SHORT).show();
            final boolean[] rm = removed.clone();
            final List<File> add = new ArrayList<>(added);
            new Thread(() -> {
                try {
                    final File out = PdfTools.editPdf(a, pdf, rm, add);
                    for (File f : add) f.delete();
                    a.runOnUiThread(() -> {
                        dlg.dismiss();
                        cb.done(PhotoFlow.uri(a, out));
                    });
                } catch (Exception | OutOfMemoryError e) {
                    a.runOnUiThread(() -> {
                        v.setEnabled(true);
                        Toast.makeText(a, "Could not save the PDF. Try again.", Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        });
    }

    private static ImageView thumb(Activity a) {
        ImageView iv = new ImageView(a);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setBackground(CaseViews.box(a, AppTheme.field(a), 8, AppTheme.fieldStroke(a), 1));
        return iv;
    }

    private static View row(Activity a, ImageView iv, String label, View.OnClickListener onDelete) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, CaseViews.dp(a, 6), 0, CaseViews.dp(a, 6));
        row.addView(iv, new LinearLayout.LayoutParams(CaseViews.dp(a, 56), CaseViews.dp(a, 78)));
        TextView t = CaseViews.text(a, label, 15, AppTheme.text(a), true);
        t.setPadding(CaseViews.dp(a, 14), 0, 0, 0);
        row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ImageView del = CaseViews.icon(a, R.drawable.ic_delete, LegalUi.RED, 40);
        int p = CaseViews.dp(a, 9);
        del.setPadding(p, p, p, p);
        del.setContentDescription("Delete " + label);
        del.setOnClickListener(onDelete);
        row.addView(del);
        return row;
    }
}
