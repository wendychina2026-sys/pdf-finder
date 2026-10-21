package com.example.twobuttons;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Rounded, theme-aware dialogs for the New Case page: the court chooser sheet, the searchable
 * court-type list, the custom court-type form and the month calendar.
 */
final class PickerDialogs {

    private static final int BLUE = 0xFF3B82F6;
    private static final int DAY_BLUE = 0xFF5B9DF5;

    private PickerDialogs() {}

    // ---------- look ----------

    private static int dp(Context c, int v) { return CaseViews.dp(c, v); }
    private static boolean dark(Context c) { return AppTheme.isDark(c); }
    static int ink(Context c) { return dark(c) ? 0xFFFFFFFF : 0xFF111827; }
    static int soft(Context c) { return dark(c) ? 0xFFC4CAE0 : 0xFF5B6275; }
    static int hair(Context c) { return dark(c) ? 0x33FFFFFF : 0x1F000000; }
    static int chip(Context c) { return dark(c) ? 0x26FFFFFF : 0xFFEEF1F8; }

    /** Dark: navy gradient (flat navy for the bottom sheet). Light: white. */
    static GradientDrawable panel(Context c, int topDp, int bottomDp, boolean flat) {
        GradientDrawable g;
        if (dark(c)) {
            if (flat) {
                g = new GradientDrawable();
                g.setColor(0xFF111B36);
            } else {
                g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        new int[]{0xFF1E2F6B, 0xFF0B1022, 0xFF1D3B8F, 0xFF04060E});
            }
            g.setStroke(dp(c, 1), 0x33FFFFFF);
        } else {
            g = new GradientDrawable();
            g.setColor(0xFFFFFFFF);
            g.setStroke(dp(c, 1), 0xFFE3E5F0);
        }
        float t = dp(c, topDp);
        float b = dp(c, bottomDp);
        g.setCornerRadii(new float[]{t, t, t, t, b, b, b, b});
        return g;
    }

    private static Dialog shell(Activity a) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return d;
    }

    private static void present(Dialog d, Activity a, View content, boolean bottom) {
        d.setContentView(content);
        d.show();
        Window w = d.getWindow();
        if (w == null) return;
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setDimAmount(0.65f);
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        if (bottom) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
        } else {
            w.setLayout((int) (dm.widthPixels * 0.9f), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
        }
    }

    private static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private static LinearLayout.LayoutParams topMargin(Context c, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topDp);
        return lp;
    }

    private static View hairline(Context c) {
        View v = new View(c);
        v.setBackgroundColor(hair(c));
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    private static View hairline(Context c, int topDp) {
        View v = hairline(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.topMargin = dp(c, topDp);
        v.setLayoutParams(lp);
        return v;
    }

    private static LinearLayout titleRow(Activity a, Dialog d, String title, String subtitle) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout t = new LinearLayout(a);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(CaseViews.text(a, title, 20, ink(a), true));
        if (subtitle != null) {
            TextView s = CaseViews.text(a, subtitle, 14, soft(a), false);
            s.setPadding(0, dp(a, 6), 0, 0);
            t.addView(s);
        }
        row.addView(t, weight());

        ImageView x = CaseViews.icon(a, R.drawable.ic_close, ink(a), 40);
        x.setPadding(dp(a, 8), dp(a, 8), dp(a, 8), dp(a, 8));
        x.setContentDescription("Close");
        x.setOnClickListener(v -> d.dismiss());
        row.addView(x);
        return row;
    }

    /** ScrollView that never grows past a share of the screen height. */
    private static final class MaxScroll extends ScrollView {
        private final int max;

        MaxScroll(Context c, float share) {
            super(c);
            max = (int) (c.getResources().getDisplayMetrics().heightPixels * share);
        }

        @Override
        protected void onMeasure(int w, int h) {
            super.onMeasure(w, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
        }
    }

    // ---------- court chooser (bottom sheet) ----------

    static void courtSheet(Activity a, String[] names, int[] icons, String selected, Consumer<String> pick) {
        Dialog d = shell(a);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a, 20), dp(a, 22), dp(a, 20), dp(a, 28));
        root.setBackground(panel(a, 28, 0, true));
        root.addView(titleRow(a, d, "Choose a court", "Choose a court to tailor the form."));

        MaxScroll optsScroll = new MaxScroll(a, 0.62f);
        LinearLayout opts = new LinearLayout(a);
        opts.setOrientation(LinearLayout.VERTICAL);
        optsScroll.addView(opts);
        root.addView(optsScroll);

        for (int i = 0; i < names.length; i++) {
            final String name = names[i];
            boolean on = name.equals(selected);
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(a, OPT_H));
            row.setPadding(dp(a, 16), dp(a, 6), dp(a, 16), dp(a, 6));
            int fill = on ? (dark(a) ? 0xFF1B2A4E : 0xFFE8F0FE) : (dark(a) ? 0xFF0D1428 : 0xFFF4F5FA);
            row.setBackground(CaseViews.box(a, fill, 16, on ? BLUE : hair(a), 1));
            row.addView(CaseViews.icon(a, icons[i], ink(a), 22));
            TextView t = CaseViews.text(a, name, 14, ink(a), false);
            t.setPadding(dp(a, 14), 0, 0, 0);
            row.addView(t, weight());
            if (on) row.addView(CaseViews.icon(a, R.drawable.ic_check_circle, BLUE, 26));
            row.setOnClickListener(v -> {
                d.dismiss();
                pick.accept(name);
            });
            LinearLayout.LayoutParams lp = topMargin(a, i == 0 ? 16 : 8);
            opts.addView(row, lp);
        }
        present(d, a, root, true);
    }

    // ---------- searchable list (court types, months, years) ----------

    /**
     * Court types: search box, pinned "Create New Court Type" row, then the list. Rows for which
     * custom says true get a "Custom" badge and a delete button.
     */
    static void courtTypes(Activity a, Supplier<List<String>> source, String selected, Runnable onCreate,
                           Predicate<String> custom, Consumer<String> onDelete, Consumer<String> onPick) {
        listDialog(a, "Select Court Type", source, selected, "Search court types...",
                "Create New Court Type", onCreate, custom, onDelete, onPick);
    }

    /** Yes / Cancel question. Does not close on an outside tap. */
    static void confirm(Activity a, String title, String message, String yes, Runnable ok) {
        android.app.AlertDialog ad = new android.app.AlertDialog.Builder(a)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(yes, (dd, w) -> ok.run())
                .setNegativeButton("Cancel", null)
                .create();
        ad.setCanceledOnTouchOutside(false);
        ad.show();
    }

    /** Every option card in the app: same height and text size. */
    private static final int OPT_H = 44;

    /** Round "+ Add New ..." card shown on top of editable option lists. */
    private static LinearLayout addCard(Activity a, String label, Runnable onClick) {
        LinearLayout add = new LinearLayout(a);
        add.setOrientation(LinearLayout.HORIZONTAL);
        add.setGravity(Gravity.CENTER_VERTICAL);
        add.setMinimumHeight(dp(a, OPT_H));
        add.setPadding(dp(a, 16), dp(a, 8), dp(a, 16), dp(a, 8));
        add.setBackground(CaseViews.box(a, dark(a) ? 0x1AFFFFFF : 0xFFEEF3FF, 16, BLUE, 1));
        add.addView(CaseViews.text(a, "+", 20, BLUE, false));
        TextView lbl = CaseViews.text(a, label, 14, ink(a), true);
        lbl.setPadding(dp(a, 12), 0, 0, 0);
        add.addView(lbl, weight());
        add.setOnClickListener(v -> onClick.run());
        return add;
    }

    private static LinearLayout optionRow(Activity a, String text, boolean sel) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(a, OPT_H));
        r.setPadding(dp(a, 16), dp(a, 6), dp(a, 12), dp(a, 6));
        int fill = sel ? (dark(a) ? 0xFF1B2A4E : 0xFFE8F0FE) : (dark(a) ? 0xFF0D1428 : 0xFFF4F5FA);
        r.setBackground(CaseViews.box(a, fill, 16, sel ? BLUE : hair(a), 1));
        r.addView(CaseViews.text(a, text, 14, ink(a), sel), weight());
        return r;
    }

    private static View selMark(Activity a, boolean sel) {
        if (sel) return CaseViews.icon(a, R.drawable.ic_check_circle, BLUE, 26);
        View ring = new View(a);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setStroke(dp(a, 2), dark(a) ? 0x66FFFFFF : 0x55000000);
        ring.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(a, 22), dp(a, 22));
        lp.leftMargin = dp(a, 4);
        lp.rightMargin = dp(a, 2);
        ring.setLayoutParams(lp);
        return ring;
    }

    /** The app's one option list: round cards, check on the chosen one. onPick gets the index. */
    static void optionSheet(Activity a, String title, String subtitle, List<String> items, int selected,
                            Consumer<Integer> onPick) {
        final Dialog d = shell(a);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a, 20), dp(a, 22), dp(a, 20), dp(a, 28));
        root.setBackground(panel(a, 28, 0, true));
        root.addView(titleRow(a, d, title, subtitle));
        final MaxScroll sc = new MaxScroll(a, 0.62f);
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        sc.addView(list);
        root.addView(sc);
        View selRow = null;
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            boolean on = i == selected;
            LinearLayout r = optionRow(a, items.get(i), on);
            r.addView(selMark(a, on));
            r.setOnClickListener(v -> {
                d.dismiss();
                onPick.accept(idx);
            });
            list.addView(r, topMargin(a, i == 0 ? 16 : 8));
            if (on) selRow = r;
        }
        final View sel = selRow;
        if (sel != null) sc.post(() -> sc.scrollTo(0, Math.max(0, sel.getTop() - dp(a, 60))));
        present(d, a, root, true);
    }

    /**
     * Option list the user edits: round cards, "Add New" card on top, rename and delete icons on
     * every row, press-and-hold a row to drag it up or down. Rename and delete ask first.
     * onMove(i, j) swaps the stages at positions i and j (always neighbours).
     */
    static void stageSheet(Activity a, String title, Supplier<List<String>> source, String selected,
                           Consumer<String> onPick, Consumer<String> onAdd,
                           BiConsumer<String, String> onRename, Consumer<String> onDelete,
                           BiConsumer<Integer, Integer> onMove) {
        final Dialog d = shell(a);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a, 20), dp(a, 22), dp(a, 20), dp(a, 28));
        root.setBackground(panel(a, 28, 0, true));
        root.addView(titleRow(a, d, title, "Pick a stage. Add, rename or delete. Press and hold to reorder."));

        final String[] sel = {selected == null ? "" : selected};
        final MaxScroll sc = new MaxScroll(a, 0.55f);
        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        sc.addView(list);
        final Runnable[] fill = new Runnable[1];

        root.addView(addCard(a, "Add New Stage", () -> customName(a, "Add New Stage", "Stage Name*",
                "Enter stage name", "Stage name is required", s -> {
                    if (hasName(source.get(), s, null)) {
                        Toast.makeText(a, "\"" + s + "\" already exists", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    onAdd.accept(s);
                    fill[0].run();
                })), topMargin(a, 16));
        root.addView(sc, topMargin(a, 4));

        final int[] dragCur = {-1};
        final float[] dragY = {0, 0};      // {finger down y, accumulated shift}

        fill[0] = () -> {
            list.removeAllViews();
            dragCur[0] = -1;
            List<String> items = source.get();
            for (int i = 0; i < items.size(); i++) {
                final String name = items.get(i);
                boolean on = name.equals(sel[0]);
                LinearLayout r = optionRow(a, name, on);
                ImageView grip = CaseViews.icon(a, R.drawable.ic_drag, soft(a), 18);
                grip.setContentDescription("Press and hold to move");
                LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(a, 22), dp(a, 22));
                gp.rightMargin = dp(a, 8);
                r.addView(grip, 0, gp);
                ImageView edit = CaseViews.icon(a, R.drawable.ic_edit, ink(a), 36);
                edit.setPadding(dp(a, 8), dp(a, 8), dp(a, 8), dp(a, 8));
                edit.setContentDescription("Rename " + name);
                edit.setOnClickListener(v -> customName(a, "Rename Stage", "Stage Name*", "Enter stage name",
                        "Stage name is required", name, "Rename", s -> {
                            if (s.equals(name)) return;
                            if (hasName(source.get(), s, name)) {
                                Toast.makeText(a, "\"" + s + "\" already exists", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            confirm(a, "Rename stage?", "\"" + name + "\" will become \"" + s
                                    + "\". Cases using it will be updated.", "Rename", () -> {
                                onRename.accept(name, s);
                                if (name.equals(sel[0])) sel[0] = s;
                                fill[0].run();
                            });
                        }));
                ImageView del = CaseViews.icon(a, R.drawable.ic_delete, LegalUi.RED, 36);
                del.setPadding(dp(a, 8), dp(a, 8), dp(a, 8), dp(a, 8));
                del.setContentDescription("Delete " + name);
                del.setOnClickListener(v -> confirm(a, "Delete stage?", "\"" + name
                        + "\" will be removed from the list. Cases keep the stage they already have.",
                        "Delete", () -> {
                            onDelete.accept(name);
                            if (name.equals(sel[0])) sel[0] = "";
                            fill[0].run();
                        }));
                r.addView(edit, new LinearLayout.LayoutParams(dp(a, 36), dp(a, 36)));
                r.addView(del, new LinearLayout.LayoutParams(dp(a, 36), dp(a, 36)));
                r.addView(selMark(a, on));
                r.setOnClickListener(v -> {
                    d.dismiss();
                    onPick.accept(name);
                });

                // press and hold -> drag up / down
                r.setOnLongClickListener(v -> {
                    dragCur[0] = list.indexOfChild(v);
                    dragY[1] = 0;
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    v.setAlpha(0.92f);
                    v.setElevation(dp(a, 8));
                    sc.requestDisallowInterceptTouchEvent(true);
                    return true;
                });
                r.setOnTouchListener((v, e) -> {
                    if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) dragY[0] = e.getRawY();
                    if (dragCur[0] < 0 || list.indexOfChild(v) != dragCur[0]) return false;
                    switch (e.getActionMasked()) {
                        case android.view.MotionEvent.ACTION_MOVE: {
                            float ty = e.getRawY() - dragY[0] + dragY[1];
                            float step = v.getHeight() + dp(a, 8);
                            int cur = dragCur[0];
                            if (ty > step * 0.55f && cur < list.getChildCount() - 1) {
                                View other = list.getChildAt(cur + 1);
                                onMove.accept(cur, cur + 1);
                                list.removeView(other);
                                list.addView(other, cur);
                                dragCur[0] = cur + 1;
                                dragY[1] -= step;
                                ty -= step;
                                v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                            } else if (ty < -step * 0.55f && cur > 0) {
                                View other = list.getChildAt(cur - 1);
                                onMove.accept(cur, cur - 1);
                                list.removeView(other);
                                list.addView(other, cur);
                                dragCur[0] = cur - 1;
                                dragY[1] += step;
                                ty += step;
                                v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                            }
                            v.setTranslationY(ty);
                            return true;
                        }
                        case android.view.MotionEvent.ACTION_UP:
                        case android.view.MotionEvent.ACTION_CANCEL:
                            sc.requestDisallowInterceptTouchEvent(false);
                            fill[0].run();
                            return true;
                        default:
                            return true;
                    }
                });
                list.addView(r, topMargin(a, i == 0 ? 8 : 8));
            }
            if (items.isEmpty()) {
                TextView none = CaseViews.text(a, "No stages. Add one above.", 14, soft(a), false);
                none.setPadding(dp(a, 6), dp(a, 18), dp(a, 6), dp(a, 6));
                list.addView(none);
            }
        };
        fill[0].run();
        present(d, a, root, true);
    }

    private static boolean hasName(List<String> items, String name, String except) {
        for (String s : items) {
            if (except != null && s.equals(except)) continue;
            if (s.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /** Bottom sheet with icon + label rows. onPick gets the row index. */
    static void actions(Activity a, String title, String[] labels, int[] icons, Consumer<Integer> onPick) {
        final Dialog d = shell(a);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(panel(a, 28, 0, false));
        LinearLayout head = titleRow(a, d, title, null);
        head.setPadding(dp(a, 22), dp(a, 18), dp(a, 14), dp(a, 12));
        root.addView(head);
        root.addView(hairline(a));
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(a, 22), dp(a, 16), dp(a, 22), dp(a, 16));
            row.addView(CaseViews.icon(a, icons[i], AppTheme.accent(a), 26));
            TextView t = CaseViews.text(a, labels[i], 16, ink(a), false);
            t.setPadding(dp(a, 16), 0, 0, 0);
            row.addView(t);
            row.setOnClickListener(v -> {
                d.dismiss();
                onPick.accept(idx);
            });
            root.addView(row);
        }
        View pad = new View(a);
        root.addView(pad, new LinearLayout.LayoutParams(1, dp(a, 12)));
        present(d, a, root, true);
    }

    /** Judges: same look as court types. Search, pinned "Add New Judge", custom badge and delete. */
    static void judges(Activity a, Supplier<List<String>> source, String selected, Runnable onCreate,
                       Predicate<String> custom, Consumer<String> onDelete, Consumer<String> onPick) {
        listDialog(a, "Select Judge", source, selected, "Search judges...",
                "Add New Judge", onCreate, custom, onDelete, onPick);
    }

    private static void pickFromList(Activity a, String title, List<String> items, int selected,
                                     Consumer<Integer> cb) {
        listDialog(a, title, () -> items, selected >= 0 ? items.get(selected) : null, null, null, null,
                null, null, s -> cb.accept(items.indexOf(s)));
    }

    private static void listDialog(Activity a, String title, Supplier<List<String>> source, String selected,
                                   String searchHint, String createLabel, Runnable onCreate,
                                   Predicate<String> custom, Consumer<String> onDelete,
                                   Consumer<String> onPick) {
        Dialog d = shell(a);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a, 22), dp(a, 20), dp(a, 22), dp(a, 18));
        root.setBackground(panel(a, 28, 28, false));
        root.addView(titleRow(a, d, title, null));
        root.addView(hairline(a, 4));

        final EditText search = searchHint == null ? null : new RoundEdit(a);
        if (search != null) {
            LinearLayout pill = new LinearLayout(a);
            pill.setOrientation(LinearLayout.HORIZONTAL);
            pill.setGravity(Gravity.CENTER_VERTICAL);
            pill.setPadding(dp(a, 14), 0, dp(a, 14), 0);
            pill.setBackground(CaseViews.box(a, chip(a), 28, hair(a), 1));
            pill.addView(CaseViews.icon(a, R.drawable.ic_search, ink(a), 22));
            search.setHint(searchHint);
            search.setHintTextColor(soft(a));
            search.setTextColor(ink(a));
            search.setTextSize(15);
            search.setSingleLine(true);
            search.setBackground(null);
            search.setPadding(dp(a, 12), dp(a, 14), 0, dp(a, 14));
            pill.addView(search, weight());
            root.addView(pill, topMargin(a, 16));
        }

        if (createLabel != null) {
            root.addView(addCard(a, createLabel, () -> {
                d.dismiss();
                if (onCreate != null) onCreate.run();
            }), topMargin(a, 16));
        }

        final MaxScroll sv = new MaxScroll(a, 0.5f);
        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        root.addView(sv);

        final Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            list.removeAllViews();
            List<String> items = source.get();
            String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
            View selRow = null;
            for (int i = 0; i < items.size(); i++) {
                final String it = items.get(i);
                if (!q.isEmpty() && !it.toLowerCase(Locale.ROOT).contains(q)) continue;
                boolean on = it.equals(selected);
                LinearLayout r = optionRow(a, it, on);
                if (custom != null && custom.test(it)) {
                    TextView badge = CaseViews.text(a, "Custom", 11, dark(a) ? 0xFF121212 : 0xFFFFFFFF, true);
                    badge.setGravity(Gravity.CENTER);
                    badge.setPadding(dp(a, 10), dp(a, 3), dp(a, 10), dp(a, 3));
                    badge.setBackground(CaseViews.box(a, AppTheme.accent(a), 14, 0, 0));
                    r.addView(badge);
                    if (onDelete != null) {
                        ImageView x = CaseViews.icon(a, R.drawable.ic_delete, LegalUi.RED, 36);
                        x.setPadding(dp(a, 8), dp(a, 8), dp(a, 8), dp(a, 8));
                        x.setContentDescription("Delete " + it);
                        final String noun = createLabel != null && createLabel.contains("Judge") ? "judge" : "court type";
                        x.setOnClickListener(v -> confirm(a, "Delete " + noun + "?",
                                "\"" + it + "\" will be removed from your list.", "Delete", () -> {
                                    onDelete.accept(it);
                                    fill[0].run();
                                }));
                        r.addView(x, new LinearLayout.LayoutParams(dp(a, 36), dp(a, 36)));
                    }
                }
                r.addView(selMark(a, on));
                r.setOnClickListener(v -> {
                    d.dismiss();
                    onPick.accept(it);
                });
                list.addView(r, topMargin(a, i == 0 ? 14 : 8));
                if (on) selRow = r;
            }
            if (list.getChildCount() == 0) {
                TextView none = CaseViews.text(a, "No matches", 14, soft(a), false);
                none.setPadding(dp(a, 14), dp(a, 18), dp(a, 14), dp(a, 18));
                list.addView(none);
            }
            final View sel = selRow;
            if (sel != null) sv.post(() -> sv.scrollTo(0, Math.max(0, sel.getTop() - dp(a, 80))));
        };
        fill[0].run();
        if (search != null) {
            search.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
                @Override public void onTextChanged(CharSequence s, int x, int y, int z) {}
                @Override public void afterTextChanged(Editable s) { fill[0].run(); }
            });
        }
        present(d, a, root, false);
    }

    // ---------- custom court type ----------

    static void customCourt(Activity a, Consumer<String> onAdd) {
        customName(a, "Create Custom Court Type", "Court Type Name*", "Enter court type name",
                "Court type name is required", onAdd);
    }

    static void customName(Activity a, String title, String labelText, String hint, String emptyMsg,
                           Consumer<String> onAdd) {
        customName(a, title, labelText, hint, emptyMsg, "", "Add", onAdd);
    }

    static void customName(Activity a, String title, String labelText, String hint, String emptyMsg,
                           String initial, String buttonLabel, Consumer<String> onAdd) {
        Dialog d = shell(a);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a, 22), dp(a, 20), dp(a, 22), dp(a, 22));
        root.setBackground(panel(a, 28, 28, false));
        root.addView(titleRow(a, d, title, null));
        root.addView(hairline(a, 4));

        TextView label = CaseViews.text(a, labelText, 14, ink(a), false);
        root.addView(label, topMargin(a, 18));

        EditText name = new RoundEdit(a);
        name.setHint(hint);
        name.setHintTextColor(soft(a));
        name.setTextColor(ink(a));
        name.setTextSize(15);
        name.setSingleLine(true);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setPadding(dp(a, 18), dp(a, 14), dp(a, 18), dp(a, 14));
        name.setBackground(CaseViews.box(a, dark(a) ? 0x66000000 : 0xFFFFFFFF, 28,
                dark(a) ? 0xFF000000 : 0xFFD9DBE6, 2));
        root.addView(name, topMargin(a, 8));
        if (initial != null && !initial.isEmpty()) {
            name.setText(initial);
            name.setSelection(initial.length());
        }

        LinearLayout btns = new LinearLayout(a);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = CaseViews.text(a, "Cancel", 15, ink(a), false);
        cancel.setGravity(Gravity.CENTER);
        cancel.setBackground(CaseViews.box(a, chip(a), 28, hair(a), 1));
        cancel.setOnClickListener(v -> d.dismiss());
        TextView add = CaseViews.text(a, buttonLabel, 15, dark(a) ? 0xFF0B1022 : 0xFFFFFFFF, true);
        add.setGravity(Gravity.CENTER);
        add.setBackground(CaseViews.box(a, BLUE, 28, 0, 0));
        add.setOnClickListener(v -> {
            String s = name.getText().toString().trim();
            if (s.isEmpty()) {
                Toast.makeText(a, emptyMsg, Toast.LENGTH_SHORT).show();
                return;
            }
            d.dismiss();
            onAdd.accept(s);
        });
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, dp(a, 52), 1f);
        l1.rightMargin = dp(a, 12);
        btns.addView(cancel, l1);
        btns.addView(add, new LinearLayout.LayoutParams(0, dp(a, 52), 1.15f));
        root.addView(btns, topMargin(a, 20));

        present(d, a, root, false);
        Window w = d.getWindow();
        if (w != null) w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        name.requestFocus();
    }

    // ---------- calendar ----------

    private static boolean sameDay(Calendar x, Calendar y) {
        return x.get(Calendar.YEAR) == y.get(Calendar.YEAR)
                && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR);
    }

    /** Month calendar; a tapped day is written to the field as dd/MM/yyyy. */
    static void calendar(Activity a, String title, EditText target) {
        Date parsed = DiaryStore.parse(target.getText().toString());
        Calendar init = null;
        if (parsed != null) {
            init = Calendar.getInstance();
            init.setTime(parsed);
        }
        calendar(a, title, init, null, c -> target.setText(DiaryStore.fmt(c.getTime())));
    }

    private static int dayKey(Calendar c) {
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR);
    }

    static final int SUNDAY_RED = 0xFFD93025;

    /**
     * The one calendar for the whole app. Sundays (court holiday) are red filled circles.
     * initial: highlighted day, may be null. min: earlier days are greyed and not tappable, may be null.
     */
    static void calendar(Activity a, String title, Calendar initial, Calendar min, Consumer<Calendar> onPick) {
        final String[] months = new String[12];
        System.arraycopy(new DateFormatSymbols(Locale.US).getMonths(), 0, months, 0, 12);
        final String[] weekdays = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

        final Calendar today = Calendar.getInstance();
        final Calendar selected = initial;
        final Calendar shown = (Calendar) (selected != null ? selected : today).clone();
        shown.set(Calendar.DAY_OF_MONTH, 1);

        Dialog d = shell(a);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(panel(a, 28, 28, false));

        LinearLayout head = titleRow(a, d, title, null);
        head.setPadding(dp(a, 22), dp(a, 18), dp(a, 14), dp(a, 14));
        root.addView(head);
        root.addView(hairline(a));

        // month + year chips
        final TextView monthTv = CaseViews.text(a, "", 16, ink(a), false);
        final TextView yearTv = CaseViews.text(a, "", 16, ink(a), false);
        LinearLayout chips = new LinearLayout(a);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER);
        chips.setPadding(0, dp(a, 14), 0, dp(a, 6));
        chips.addView(chipBox(a, monthTv));
        View gap = new View(a);
        chips.addView(gap, new LinearLayout.LayoutParams(dp(a, 12), 1));
        chips.addView(chipBox(a, yearTv));
        root.addView(chips);

        // previous / next + title
        final TextView navTitle = CaseViews.text(a, "", 17, ink(a), true);
        navTitle.setGravity(Gravity.CENTER);
        LinearLayout nav = new LinearLayout(a);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(a, 14), dp(a, 8), dp(a, 14), dp(a, 4));
        ImageView prev = CaseViews.icon(a, R.drawable.ic_chevron_right, ink(a), 44);
        prev.setRotation(180f);
        prev.setPadding(dp(a, 10), dp(a, 10), dp(a, 10), dp(a, 10));
        prev.setContentDescription("Previous month");
        ImageView next = CaseViews.icon(a, R.drawable.ic_chevron_right, ink(a), 44);
        next.setPadding(dp(a, 10), dp(a, 10), dp(a, 10), dp(a, 10));
        next.setContentDescription("Next month");
        nav.addView(prev);
        nav.addView(navTitle, weight());
        nav.addView(next);
        root.addView(nav);

        // weekday names
        LinearLayout wk = new LinearLayout(a);
        wk.setOrientation(LinearLayout.HORIZONTAL);
        wk.setPadding(dp(a, 12), dp(a, 6), dp(a, 12), dp(a, 4));
        for (String w : weekdays) {
            TextView t = CaseViews.text(a, w, 13, w.equals("Sun") ? SUNDAY_RED : ink(a), w.equals("Sun"));
            t.setGravity(Gravity.CENTER);
            wk.addView(t, weight());
        }
        root.addView(wk);

        final LinearLayout grid = new LinearLayout(a);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(a, 12), 0, dp(a, 12), dp(a, 18));
        root.addView(grid);

        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            int month = shown.get(Calendar.MONTH);
            int year = shown.get(Calendar.YEAR);
            monthTv.setText(months[month]);
            yearTv.setText(String.valueOf(year));
            navTitle.setText(months[month] + " " + year);
            grid.removeAllViews();

            int offset = shown.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
            int dim = shown.getActualMaximum(Calendar.DAY_OF_MONTH);
            int rows = (offset + dim + 6) / 7;
            Calendar cur = (Calendar) shown.clone();
            cur.add(Calendar.DAY_OF_MONTH, -offset);
            Calendar mark = selected != null ? selected : today;
            int fade = (ink(a) & 0x00FFFFFF) | 0x66000000;

            for (int r = 0; r < rows; r++) {
                LinearLayout row = new LinearLayout(a);
                row.setOrientation(LinearLayout.HORIZONTAL);
                for (int c = 0; c < 7; c++) {
                    final Calendar day = (Calendar) cur.clone();
                    boolean inMonth = day.get(Calendar.MONTH) == month;
                    TextView t = CaseViews.text(a, String.valueOf(day.get(Calendar.DAY_OF_MONTH)), 16,
                            inMonth ? ink(a) : fade, false);
                    t.setGravity(Gravity.CENTER);
                    boolean sunday = day.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY;
                    boolean blocked = min != null && dayKey(day) < dayKey(min);
                    boolean chosen = sameDay(day, mark);
                    if (sunday) {
                        t.setBackground(CaseViews.box(a, SUNDAY_RED, 20, chosen ? ink(a) : 0, chosen ? 2 : 0));
                        t.setTextColor(Color.WHITE);
                        if (!inMonth) t.setAlpha(0.4f);
                    } else if (chosen) {
                        t.setBackground(CaseViews.box(a, DAY_BLUE, 20, 0, 0));
                        t.setTextColor(Color.WHITE);
                    } else if (sameDay(day, today)) {
                        t.setBackground(CaseViews.box(a, Color.TRANSPARENT, 20, DAY_BLUE, 1));
                    }
                    if (blocked) {
                        t.setAlpha(0.3f);
                    } else {
                        t.setOnClickListener(v -> {
                            onPick.accept(day);
                            d.dismiss();
                        });
                    }
                    FrameLayout cell = new FrameLayout(a);
                    cell.addView(t, new FrameLayout.LayoutParams(dp(a, 40), dp(a, 40), Gravity.CENTER));
                    row.addView(cell, new LinearLayout.LayoutParams(0, dp(a, 46), 1f));
                    cur.add(Calendar.DAY_OF_MONTH, 1);
                }
                grid.addView(row);
            }
        };

        prev.setOnClickListener(v -> {
            shown.add(Calendar.MONTH, -1);
            render[0].run();
        });
        next.setOnClickListener(v -> {
            shown.add(Calendar.MONTH, 1);
            render[0].run();
        });
        ((View) monthTv.getParent()).setOnClickListener(v ->
                pickFromList(a, "Select Month", java.util.Arrays.asList(months),
                        shown.get(Calendar.MONTH), i -> {
                            shown.set(Calendar.MONTH, i);
                            render[0].run();
                        }));
        final int firstYear = today.get(Calendar.YEAR) - 80;
        ((View) yearTv.getParent()).setOnClickListener(v -> {
            List<String> years = new ArrayList<>();
            for (int y = firstYear; y <= today.get(Calendar.YEAR) + 10; y++) years.add(String.valueOf(y));
            pickFromList(a, "Select Year", years, shown.get(Calendar.YEAR) - firstYear, i -> {
                shown.set(Calendar.YEAR, firstYear + i);
                render[0].run();
            });
        });

        render[0].run();
        present(d, a, root, false);
    }

    private static LinearLayout chipBox(Activity a, TextView label) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(a, 18), dp(a, 10), dp(a, 12), dp(a, 10));
        box.setBackground(CaseViews.box(a, chip(a), 24, hair(a), 1));
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(label);
        ImageView arrow = CaseViews.icon(a, R.drawable.ic_expand_more, ink(a), 20);
        box.addView(arrow);
        return box;
    }
}
