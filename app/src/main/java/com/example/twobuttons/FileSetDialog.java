package com.example.twobuttons;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Manage ALL files of one document group: view, replace, delete, move up / down, add more.
 * The host activity forwards onActivityResult to {@link #handle}.
 */
final class FileSetDialog {

    static final int REQ = 71;
    private static final String[] SRC = {"Upload PDF / Word / image", "Take photo"};
    private static final int[] SRC_ICONS = {R.drawable.ic_pdf, R.drawable.ic_camera};

    interface Done { void save(List<Uri> files); }

    private static List<Uri> curFiles;
    private static int pendIdx = -1;
    private static Runnable pendRedraw;

    private FileSetDialog() { }

    static String displayName(Activity a, Uri u) {
        String name = null;
        try (Cursor c = a.getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int col = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (col >= 0) name = c.getString(col);
            }
        } catch (Exception ignored) {
        }
        if (name == null || name.isEmpty()) name = u.getLastPathSegment();
        return name == null ? "File" : name;
    }

    static long size(Activity a, Uri u) {
        try (Cursor c = a.getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int col = c.getColumnIndex(OpenableColumns.SIZE);
                if (col >= 0 && !c.isNull(col)) return c.getLong(col);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    static String totalSize(Activity a, List<Uri> files) {
        long b = 0;
        for (Uri u : files) b += size(a, u);
        if (b <= 0) return "";
        if (b < 1024 * 1024) return String.format(Locale.US, "%.1f KB", b / 1024.0);
        return String.format(Locale.US, "%.1f MB", b / (1024.0 * 1024.0));
    }

    /** File name for one file, "name +N more" for a group. */
    static String label(Activity a, List<Uri> files) {
        String n = displayName(a, files.get(0));
        return files.size() == 1 ? n : n + " +" + (files.size() - 1) + " more";
    }

    static void show(final Activity a, final PhotoFlow flow, String title, List<Uri> start, final Done done) {
        final List<Uri> files = new ArrayList<>(start);
        final LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        final Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            body.removeAllViews();
            TextView hint = CaseViews.text(a, files.size() + (files.size() == 1 ? " file" : " files")
                    + " in this document. Use the arrows to change the order.", 12, AppTheme.sub(a), false);
            hint.setPadding(0, dp(a, 6), 0, dp(a, 8));
            body.addView(hint);
            for (int i = 0; i < files.size(); i++) {
                final int idx = i;
                final Uri u = files.get(i);
                final String nm = displayName(a, u);
                final int k = DocFileCard.kind(nm);
                LinearLayout card = new LinearLayout(a);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10));
                card.setBackground(CaseViews.box(a, AppTheme.field(a), 14, AppTheme.fieldStroke(a), 1));
                LinearLayout top = new LinearLayout(a);
                top.setOrientation(LinearLayout.HORIZONTAL);
                top.setGravity(Gravity.CENTER_VERTICAL);
                top.addView(CaseViews.text(a, String.valueOf(i + 1), 13, AppTheme.sub(a), true));
                View ic = CaseViews.icon(a, DocFileCard.kindIcon(k), DocFileCard.kindColor(a, k), 20);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(a, 20), dp(a, 20));
                ilp.leftMargin = dp(a, 10);
                top.addView(ic, ilp);
                TextView t = CaseViews.text(a, nm, 13.5f, AppTheme.text(a), true);
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                t.setPadding(dp(a, 8), 0, 0, 0);
                top.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                card.addView(top);
                LinearLayout btns = new LinearLayout(a);
                btns.setOrientation(LinearLayout.HORIZONTAL);
                btns.addView(btn(a, "View", AppTheme.info(a), () -> DocFileCard.open(a, u, nm)), blp(a, 0, 1f));
                btns.addView(btn(a, "Replace", AppTheme.accent(a), () -> {
                    Runnable go = () -> {
                        pendIdx = idx;
                        pendRedraw = draw[0];
                        curFiles = files;
                        source(a, flow);
                    };
                    File vf = DataFolder.documentsByUri(a).get(u.toString());
                    if (vf != null && !new MarkStore(a).all(vf.getAbsolutePath()).isEmpty()) {
                        PickerDialogs.confirm(a, "Replace file?",
                                "Page marks of this file will be reset. You mark the new file right after.",
                                "Replace", go);
                    } else {
                        go.run();
                    }
                }), blp(a, 6, 1f));
                btns.addView(btn(a, "Delete", RoundEdit.RED, () -> {
                    files.remove(idx);
                    draw[0].run();
                }), blp(a, 6, 1f));
                if (idx > 0) btns.addView(btn(a, "\u2191", AppTheme.text(a), () -> {
                    java.util.Collections.swap(files, idx, idx - 1);
                    draw[0].run();
                }), blp(a, 6, 0.5f));
                if (idx < files.size() - 1) btns.addView(btn(a, "\u2193", AppTheme.text(a), () -> {
                    java.util.Collections.swap(files, idx, idx + 1);
                    draw[0].run();
                }), blp(a, 6, 0.5f));
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                bl.topMargin = dp(a, 8);
                card.addView(btns, bl);
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                cl.bottomMargin = dp(a, 8);
                body.addView(card, cl);
            }
            LinearLayout add = LegalUi.chipButton(a, "Add more files",
                    IconPalette.pick(a, 0xFF1565C0, 0xFF64B5F6), R.drawable.ic_add_circle);
            add.setOnClickListener(v -> {
                pendIdx = -1;
                pendRedraw = draw[0];
                curFiles = files;
                source(a, flow);
            });
            body.addView(add);
        };
        draw[0].run();
        PickerDialogs.formSheet(a, title, null, body, "Save changes", () -> {
            if (files.isEmpty()) {
                Toast.makeText(a, "Keep at least one file, or delete the whole document instead",
                        Toast.LENGTH_LONG).show();
                return false;
            }
            done.save(new ArrayList<>(files));
            return true;
        }, () -> { curFiles = null; pendRedraw = null; });
    }

    private static void source(final Activity a, final PhotoFlow flow) {
        PickerDialogs.actions(a, "Add files", SRC, SRC_ICONS, i -> {
            if (i == 0) {
                Intent in = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                in.addCategory(Intent.CATEGORY_OPENABLE);
                in.setType("*/*");
                in.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf", "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "image/*"});
                in.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, pendIdx < 0);
                in.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                try {
                    a.startActivityForResult(in, REQ);
                } catch (Exception e) {
                    Toast.makeText(a, "No file picker available on this device", Toast.LENGTH_LONG).show();
                }
            } else {
                flow.start(true, new PhotoFlow.Result() {
                    @Override public void images(List<File> fs) {
                        List<Uri> u = new ArrayList<>();
                        for (File f : fs) u.add(PhotoFlow.uri(a, f));
                        put(a, u);
                    }
                    @Override public void pdf(File f) {
                        List<Uri> u = new ArrayList<>();
                        u.add(PhotoFlow.uri(a, f));
                        put(a, u);
                    }
                });
            }
        });
    }

    private static void put(Activity a, List<Uri> uris) {
        if (curFiles == null || uris.isEmpty()) return;
        if (pendIdx >= 0 && pendIdx < curFiles.size()) {
            curFiles.set(pendIdx, uris.get(0));
        } else {
            for (Uri u : uris) if (!curFiles.contains(u)) curFiles.add(u);
        }
        if (pendRedraw != null) pendRedraw.run();
    }

    /** Forward the host's onActivityResult here. True when it was ours. */
    static boolean handle(Activity a, int req, int res, Intent data) {
        if (req != REQ) return false;
        if (res != Activity.RESULT_OK || data == null) return true;
        List<Uri> got = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) got.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            got.add(data.getData());
        }
        for (Uri u : got) {
            try {
                a.getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
            }
        }
        put(a, got);
        return true;
    }

    private static int dp(Activity a, int v) { return CaseViews.dp(a, v); }

    private static LinearLayout.LayoutParams blp(Activity a, int left, float w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(a, 34), w);
        lp.leftMargin = dp(a, left);
        return lp;
    }

    private static View btn(Activity a, String label, int color, Runnable click) {
        LinearLayout b = new LinearLayout(a);
        b.setGravity(Gravity.CENTER);
        b.setBackground(CaseViews.box(a, (color & 0x00FFFFFF) | 0x1A000000, 10, (color & 0x00FFFFFF) | 0x66000000, 1));
        b.addView(CaseViews.text(a, label, 12, color, true));
        b.setOnClickListener(v -> click.run());
        return b;
    }
}
