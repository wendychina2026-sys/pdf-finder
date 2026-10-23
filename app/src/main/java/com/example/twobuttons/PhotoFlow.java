package com.example.twobuttons;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Camera, any number of photos in a row. After each shot a review list shows every photo with its
 * own delete button; Done then saves them as one PDF or as separate images (askFormat), or just
 * hands the photo files back (used when adding pages to an existing PDF).
 * The host activity forwards onActivityResult to handle().
 */
final class PhotoFlow {
    interface Result {
        void images(List<File> files);
        void pdf(File file);
        default void cancelled() {}
    }

    private final Activity a;
    private final int req;
    private final List<File> shots = new ArrayList<>();
    private final Map<File, Bitmap> thumbs = new HashMap<>();
    private File cur;
    private boolean askFormat;
    private Result result;

    PhotoFlow(Activity a, int req) {
        this.a = a;
        this.req = req;
    }

    static Uri uri(Context c, File f) {
        return FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", f);
    }

    void start(boolean askFormat, Result r) {
        for (File f : shots) f.delete();
        shots.clear();
        thumbs.clear();
        this.askFormat = askFormat;
        this.result = r;
        shoot();
    }

    /** Returns true when the result belonged to this flow. */
    boolean handle(int rq, int res) {
        if (rq != req) return false;
        if (res == Activity.RESULT_OK && cur != null && cur.length() > 0) shots.add(cur);
        else if (cur != null) cur.delete();
        cur = null;
        if (shots.isEmpty()) {
            if (result != null) result.cancelled();
        } else {
            review();
        }
        return true;
    }

    private void shoot() {
        try {
            cur = PdfTools.newFile(a, "Photo_", ".jpg");
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, uri(a, cur));
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivityForResult(i, req);
        } catch (Exception e) {
            Toast.makeText(a, "Camera not available", Toast.LENGTH_LONG).show();
            if (shots.isEmpty()) {
                if (result != null) result.cancelled();
            } else {
                review();
            }
        }
    }

    private int dp(int v) {
        return CaseViews.dp(a, v);
    }

    private void review() {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(4), dp(16), 0);
        ScrollView sv = new ScrollView(a);
        sv.addView(box);
        final AlertDialog dlg = new AlertDialog.Builder(a)
                .setView(sv)
                .setCancelable(false)
                .setPositiveButton("Done", (d, w) -> onDone())
                .setNegativeButton("Add more", (d, w) -> shoot())
                .setNeutralButton("Discard all", (d, w) -> discardAll())
                .create();
        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            box.removeAllViews();
            dlg.setTitle(shots.size() + (shots.size() == 1 ? " photo" : " photos"));
            for (int i = 0; i < shots.size(); i++) {
                final File f = shots.get(i);
                LinearLayout row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(10), dp(10), dp(6), dp(10));
                row.setBackground(CaseViews.box(a, AppTheme.card(a), 16, AppTheme.cardStroke(a), 1));
                ImageView iv = new ImageView(a);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setBackground(CaseViews.box(a, AppTheme.field(a), 8, AppTheme.fieldStroke(a), 1));
                Bitmap b = thumbs.get(f);
                if (b == null) {
                    try {
                        b = PdfTools.decode(f, 240);
                        if (b != null) thumbs.put(f, b);
                    } catch (Exception ignored) {
                        // no preview
                    }
                }
                if (b != null) iv.setImageBitmap(b);
                row.addView(iv, new LinearLayout.LayoutParams(dp(64), dp(64)));
                TextView t = CaseViews.text(a, "Photo " + (i + 1), 15, AppTheme.text(a), true);
                t.setPadding(dp(14), 0, 0, 0);
                row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                ImageView del = CaseViews.icon(a, R.drawable.ic_delete, LegalUi.RED, 40);
                del.setPadding(dp(9), dp(9), dp(9), dp(9));
                del.setContentDescription("Delete photo " + (i + 1));
                del.setOnClickListener(v -> {
                    shots.remove(f);
                    f.delete();
                    thumbs.remove(f);
                    if (shots.isEmpty()) {
                        dlg.dismiss();
                        if (result != null) result.cancelled();
                    } else {
                        fill[0].run();
                    }
                });
                row.addView(del);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = dp(i == 0 ? 4 : 8);
                box.addView(row, rlp);
            }
            // one photo: the bin already removes it, "Discard all" only makes sense for several
            android.widget.Button all = dlg.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (all != null) all.setVisibility(shots.size() > 1 ? View.VISIBLE : View.GONE);
        };
        fill[0].run();
        dlg.show();
        fill[0].run();
    }

    private void discardAll() {
        for (File f : shots) f.delete();
        shots.clear();
        thumbs.clear();
        if (result != null) result.cancelled();
    }

    private void onDone() {
        if (!askFormat) {
            List<File> out = new ArrayList<>(shots);
            shots.clear();
            thumbs.clear();
            result.images(out);
            return;
        }
        final boolean one = shots.size() == 1;
        String[] items = one
                ? new String[]{"Save as PDF", "Save as image (JPG)"}
                : new String[]{"One PDF (each photo is a page)", "Images (each photo separate)"};
        new AlertDialog.Builder(a)
                .setTitle("Save as")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        makePdf();
                    } else {
                        List<File> out = new ArrayList<>(shots);
                        shots.clear();
                        thumbs.clear();
                        result.images(out);
                    }
                })
                .setOnCancelListener(d -> review())
                .show();
    }

    private void makePdf() {
        final List<File> src = new ArrayList<>(shots);
        Toast.makeText(a, "Making PDF...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                final File out = PdfTools.makePdf(a, src);
                for (File f : src) f.delete();
                a.runOnUiThread(() -> {
                    shots.clear();
                    thumbs.clear();
                    result.pdf(out);
                });
            } catch (Exception | OutOfMemoryError e) {
                a.runOnUiThread(() -> {
                    Toast.makeText(a, "Could not make the PDF. Try again.", Toast.LENGTH_LONG).show();
                    review();
                });
            }
        }).start();
    }
}
