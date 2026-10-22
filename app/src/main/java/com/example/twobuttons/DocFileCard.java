package com.example.twobuttons;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * THE uploaded-file card (New Case upload list + Document Library): category chip, size, open arrow,
 * type icon (PDF / Word / image), name, "Attached to", date and Open / Save / Replace / Share / Delete.
 * Header tap opens / closes the full details (same chevron as Tomorrow / Upcoming hearings).
 */
final class DocFileCard {

    static final class Info {
        String name = "", fileName = "", category = "", matter = "", stage = "", size = "", date = "";
        Uri uri;
    }

    static final class Actions {
        Runnable open, save, replace, share, delete;
    }

    static final int PDF_RED = 0xFFE5484D, WORD_BLUE = 0xFF3B82F6, IMG_GREEN = 0xFF22C55E;
    private static Uri pendingSave;

    private DocFileCard() { }

    static String ext(String n) {
        int dot = n.lastIndexOf('.');
        return dot > 0 && dot >= n.length() - 6 ? n.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    static String stem(String n) {
        int dot = n.lastIndexOf('.');
        return dot > 0 && dot >= n.length() - 6 ? n.substring(0, dot) : n;
    }

    /** 0 pdf, 1 word, 2 image, 3 other. */
    static int kind(String name) {
        String e = ext(name);
        if (e.equals("pdf")) return 0;
        if (e.equals("doc") || e.equals("docx")) return 1;
        if (e.equals("jpg") || e.equals("jpeg") || e.equals("png") || e.equals("webp")) return 2;
        return 3;
    }

    static int kindIcon(int k) {
        return k == 1 ? R.drawable.ic_word : k == 2 ? R.drawable.ic_image : R.drawable.ic_pdf;
    }

    static int kindColor(android.content.Context c, int k) {
        return k == 0 ? PDF_RED : k == 1 ? WORD_BLUE : k == 2 ? IMG_GREEN : AppTheme.sub(c);
    }

    static String kindLabel(int k, String name) {
        return k == 0 ? "PDF" : k == 1 ? "Word" : k == 2 ? "Image"
                : ext(name).isEmpty() ? "File" : ext(name).toUpperCase(Locale.ROOT);
    }

    private static int dp(android.content.Context c, int v) { return CaseViews.dp(c, v); }

    static LinearLayout build(Activity a, Info in, Actions act) {
        final int k = kind(in.name);
        final int kc = kindColor(a, k);
        final boolean dark = AppTheme.isDark(a);
        final int gold = dark ? 0xFFF0B03A : 0xFFB7791F;

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10));
        card.setBackground(CaseViews.box(a, AppTheme.field(a), 14, AppTheme.fieldStroke(a), 1));

        LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.VERTICAL);
        card.addView(top);

        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        String cat = in.category.isEmpty() ? "DOCUMENT" : in.category.toUpperCase(Locale.ROOT);
        TextView chip = CaseViews.text(a, cat, 11, gold, true);
        chip.setSingleLine(true);
        chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chip.setPadding(dp(a, 10), dp(a, 4), dp(a, 10), dp(a, 4));
        chip.setBackground(CaseViews.box(a, (gold & 0x00FFFFFF) | 0x22000000, 8, (gold & 0x00FFFFFF) | 0x66000000, 1));
        head.addView(chip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        View sp = new View(a);
        head.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        String meta = kindLabel(k, in.name) + (in.size.isEmpty() ? "" : "  \u2022  " + in.size);
        TextView metaTv = CaseViews.text(a, meta, 12, AppTheme.sub(a), true);
        head.addView(metaTv);
        final ImageView chev = CaseViews.icon(a, R.drawable.ic_expand_more, AppTheme.text(a), 22);
        LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) chev.getLayoutParams();
        clp.leftMargin = dp(a, 6);
        head.addView(chev, clp);
        top.addView(head);

        LinearLayout title = new LinearLayout(a);
        title.setOrientation(LinearLayout.HORIZONTAL);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(0, dp(a, 6), 0, 0);
        title.addView(CaseViews.icon(a, kindIcon(k), kc, 20));
        TextView nm = CaseViews.text(a, stem(in.name), 14, AppTheme.text(a), true);
        nm.setSingleLine(true);
        nm.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        nm.setPadding(dp(a, 8), 0, 0, 0);
        title.addView(nm, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(title);

        LinearLayout att = new LinearLayout(a);
        att.setOrientation(LinearLayout.HORIZONTAL);
        att.setGravity(Gravity.CENTER_VERTICAL);
        att.setPadding(0, dp(a, 4), 0, 0);
        att.addView(CaseViews.text(a, "Attached to: ", 11, AppTheme.sub(a), true));
        String matter = in.matter.isEmpty() ? "This case" : in.matter;
        TextView mt = CaseViews.text(a, matter, 12, AppTheme.text(a), false);
        mt.setSingleLine(true);
        mt.setEllipsize(android.text.TextUtils.TruncateAt.END);
        att.addView(mt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (!in.date.isEmpty()) {
            TextView d = CaseViews.text(a, in.date, 11, AppTheme.sub(a), false);
            d.setPadding(dp(a, 8), 0, 0, 0);
            att.addView(d);
        }
        top.addView(att);

        // full details, hidden until the arrow is tapped
        final LinearLayout details = new LinearLayout(a);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setVisibility(View.GONE);
        details.setPadding(0, dp(a, 6), 0, 0);
        // category, size, type and date already show on the card: only what is not visible
        detail(a, details, "File name", in.fileName.isEmpty() ? in.name : in.fileName);
        detail(a, details, "Stage", in.stage);
        card.addView(details);

        top.setOnClickListener(v -> {
            boolean open = details.getVisibility() != View.VISIBLE;
            details.setVisibility(open ? View.VISIBLE : View.GONE);
            chev.animate().rotation(open ? 180f : 0f).setDuration(180).start();
        });

        View line = new View(a);
        line.setBackgroundColor(AppTheme.cardStroke(a));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
        llp.topMargin = dp(a, 8);
        card.addView(line, llp);

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int info = AppTheme.info(a), green = AppTheme.topics(a);
        if (act.open != null) row.addView(labelBtn(a, R.drawable.ic_eye, info, "Open", act.open), wlp(a, 0));
        if (act.save != null) row.addView(labelBtn(a, R.drawable.ic_download, green, "Save", act.save), wlp(a, 8));
        if (act.replace != null) row.addView(iconBtn(a, R.drawable.ic_replace, gold, "Replace file", act.replace), ilp(a));
        if (act.share != null) row.addView(iconBtn(a, R.drawable.ic_share, AppTheme.sub(a), "Share file", act.share), ilp(a));
        if (act.delete != null) row.addView(iconBtn(a, R.drawable.ic_delete, RoundEdit.RED, "Delete file", act.delete), ilp(a));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(a, 8);
        card.addView(row, rlp);
        return card;
    }

    private static void detail(Activity a, LinearLayout box, String label, String value) {
        if (value == null || value.isEmpty()) return;
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(a, 3), 0, dp(a, 3));
        TextView l = CaseViews.text(a, label, 12, AppTheme.sub(a), true);
        r.addView(l, new LinearLayout.LayoutParams(dp(a, 84), LinearLayout.LayoutParams.WRAP_CONTENT));
        r.addView(CaseViews.text(a, value, 13, AppTheme.text(a), false), LegalUi.weight());
        box.addView(r);
    }

    private static LinearLayout.LayoutParams wlp(Activity a, int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(a, 32), 1f);
        lp.leftMargin = dp(a, left);
        return lp;
    }

    private static LinearLayout.LayoutParams ilp(Activity a) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(a, 32), dp(a, 32));
        lp.leftMargin = dp(a, 8);
        return lp;
    }

    private static View labelBtn(Activity a, int icon, int color, String label, Runnable click) {
        LinearLayout b = new LinearLayout(a);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        b.setBackground(CaseViews.box(a, (color & 0x00FFFFFF) | 0x1A000000, 10, (color & 0x00FFFFFF) | 0x66000000, 1));
        b.addView(CaseViews.icon(a, icon, color, 14));
        TextView t = CaseViews.text(a, label, 12, color, true);
        t.setPadding(dp(a, 5), 0, 0, 0);
        b.addView(t);
        b.setOnClickListener(v -> click.run());
        return b;
    }

    private static View iconBtn(Activity a, int icon, int color, String desc, Runnable click) {
        LinearLayout b = new LinearLayout(a);
        b.setGravity(Gravity.CENTER);
        b.setBackground(CaseViews.box(a, (color & 0x00FFFFFF) | 0x1A000000, 10, 0, 0));
        ImageView i = CaseViews.icon(a, icon, color, 16);
        b.addView(i);
        b.setContentDescription(desc);
        b.setOnClickListener(v -> click.run());
        return b;
    }

    // ---------- shared file actions ----------

    static String mime(Activity a, Uri u, String name) {
        String t = u == null ? null : a.getContentResolver().getType(u);
        if (t != null) return t;
        switch (kind(name)) {
            case 0: return "application/pdf";
            case 1: return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case 2: return "image/*";
            default: return "*/*";
        }
    }

    static void open(Activity a, Uri u, String name) {
        if (u == null) { Toast.makeText(a, "File not stored on this device", Toast.LENGTH_SHORT).show(); return; }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(u, mime(a, u, name));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            a.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(a, "No app found to open this file", Toast.LENGTH_SHORT).show();
        }
    }

    static void share(Activity a, Uri u, String name) {
        if (u == null) { Toast.makeText(a, "File not stored on this device", Toast.LENGTH_SHORT).show(); return; }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime(a, u, name));
        i.putExtra(Intent.EXTRA_STREAM, u);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            a.startActivity(Intent.createChooser(i, "Share file"));
        } catch (Exception e) {
            Toast.makeText(a, "Cannot share this file", Toast.LENGTH_SHORT).show();
        }
    }

    /** Ask where to save a copy; call finishSave from onActivityResult(req). */
    static void startSave(Activity a, int req, Uri u, String name) {
        if (u == null) { Toast.makeText(a, "File not stored on this device", Toast.LENGTH_SHORT).show(); return; }
        pendingSave = u;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime(a, u, name).equals("image/*") ? "image/jpeg" : mime(a, u, name));
        i.putExtra(Intent.EXTRA_TITLE, name);
        try {
            a.startActivityForResult(i, req);
        } catch (Exception e) {
            Toast.makeText(a, "No file manager available", Toast.LENGTH_SHORT).show();
        }
    }

    static void finishSave(Activity a, int res, Intent data) {
        Uri src = pendingSave;
        pendingSave = null;
        if (res != Activity.RESULT_OK || data == null || data.getData() == null || src == null) return;
        try (InputStream in = a.getContentResolver().openInputStream(src);
             OutputStream out = a.getContentResolver().openOutputStream(data.getData())) {
            byte[] buf = new byte[16384];
            int n;
            while (in != null && out != null && (n = in.read(buf)) > 0) out.write(buf, 0, n);
            Toast.makeText(a, "Saved", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(a, "Could not save file", Toast.LENGTH_LONG).show();
        }
    }
}
