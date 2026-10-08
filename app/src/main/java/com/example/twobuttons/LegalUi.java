package com.example.twobuttons;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/** Small view + dialog helpers shared by the Clients / Documents / Reminders pages. */
final class LegalUi {

    static final String TAKA = "\u09F3";
    static final int RED = 0xFFD32F2F;
    static final int GREEN = 0xFF2E7D32;

    private LegalUi() {}

    static int dp(Context c, int v) { return CaseViews.dp(c, v); }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        l.setBackground(CaseViews.box(c, AppTheme.card(c), 12, AppTheme.cardStroke(c), 1));
        return l;
    }

    static TextView sectionTitle(Context c, String s) {
        TextView t = CaseViews.text(c, s, 18, AppTheme.text(c), true);
        t.setPadding(0, dp(c, 20), 0, dp(c, 8));
        return t;
    }

    static TextView chip(Context c, String s, int fill, int textColor) {
        TextView t = CaseViews.text(c, s, 12, textColor, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 12), dp(c, 5), dp(c, 12), dp(c, 5));
        t.setBackground(CaseViews.box(c, fill, 14, 0, 0));
        return t;
    }

    static String money(long v) {
        return TAKA + String.format(Locale.US, "%,d", v);
    }

    static String str(EditText e) { return e.getText().toString().trim(); }

    static long num(EditText e) {
        String d = e.getText().toString().replaceAll("[^0-9]", "");
        if (d.isEmpty()) return 0;
        try { return Long.parseLong(d); } catch (NumberFormatException ex) { return 0; }
    }

    static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    /** Home-page row that opens one of the three pages. */
    static LinearLayout navRow(Activity a, int iconRes, String title, String sub, Class<?> target) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(a, 14), dp(a, 12), dp(a, 14), dp(a, 12));
        row.setBackground(CaseViews.box(a, AppTheme.card(a), 12, AppTheme.cardStroke(a), 1));
        row.addView(CaseViews.icon(a, iconRes, AppTheme.accent(a), 26));
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(a, 14), 0, 0, 0);
        col.addView(CaseViews.text(a, title, 16, AppTheme.text(a), true));
        col.addView(CaseViews.text(a, sub, 13, AppTheme.sub(a), false));
        row.addView(col, weight());
        row.setOnClickListener(v -> a.startActivity(new Intent(a, target)));
        return row;
    }

    // ---------------- forms ----------------

    static LinearLayout form(Context c) {
        LinearLayout f = new LinearLayout(c);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(dp(c, 20), dp(c, 8), dp(c, 20), dp(c, 8));
        return f;
    }

    private static void label(Context c, LinearLayout f, String s) {
        TextView t = CaseViews.text(c, s, 12, AppTheme.sub(c), true);
        t.setPadding(0, dp(c, 10), 0, 0);
        f.addView(t);
    }

    static EditText input(Context c, LinearLayout f, String label, String value, int type) {
        label(c, f, label);
        EditText e = new EditText(c);
        e.setInputType(type);
        e.setText(value);
        e.setTextSize(15);
        f.addView(e);
        return e;
    }

    static EditText text(Context c, LinearLayout f, String label, String value) {
        return input(c, f, label, value, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    }

    static EditText multiline(Context c, LinearLayout f, String label, String value) {
        EditText e = input(c, f, label, value, InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setMinLines(5);
        e.setGravity(Gravity.TOP);
        return e;
    }

    /** Tap -> date picker. Format dd MMM yyyy. */
    static EditText dateInput(Context c, LinearLayout f, String label, String value) {
        EditText e = input(c, f, label, value, InputType.TYPE_NULL);
        e.setFocusable(false);
        e.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy", Locale.US);
            try {
                Date d = fmt.parse(e.getText().toString().trim());
                if (d != null) cal.setTime(d);
            } catch (Exception ignored) {
            }
            new DatePickerDialog(c, (view, y, m, day) -> {
                Calendar x = Calendar.getInstance();
                x.set(y, m, day);
                e.setText(fmt.format(x.getTime()));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        return e;
    }

    static Spinner spinner(Context c, LinearLayout f, String label, String[] opts, String selected) {
        label(c, f, label);
        Spinner s = new Spinner(c);
        s.setAdapter(new ArrayAdapter<>(c, android.R.layout.simple_spinner_dropdown_item, opts));
        for (int i = 0; i < opts.length; i++) if (opts[i].equalsIgnoreCase(selected)) s.setSelection(i);
        f.addView(s);
        return s;
    }

    /** Save stays open if onSave returns false. onDelete may be null (no Delete button). */
    static void formDialog(Activity a, String title, View form, BooleanSupplier onSave, Runnable onDelete) {
        ScrollView sv = new ScrollView(a);
        sv.addView(form);
        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(title)
                .setView(sv)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null);
        if (onDelete != null) b.setNeutralButton("Delete", null);
        AlertDialog d = b.create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (onSave.getAsBoolean()) d.dismiss();
        });
        if (onDelete != null) {
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                    confirm(a, "Delete this?", "This cannot be undone.", () -> {
                        d.dismiss();
                        onDelete.run();
                    }));
        }
    }

    static void confirm(Activity a, String title, String msg, Runnable yes) {
        new AlertDialog.Builder(a)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("Delete", (d, w) -> yes.run())
                .setNegativeButton("Cancel", null)
                .show();
    }

    static boolean need(Activity a, String value, String what) {
        if (!value.isEmpty()) return true;
        Toast.makeText(a, what + " is required", Toast.LENGTH_SHORT).show();
        return false;
    }

    static Button outlineButton(Context c, String label) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(AppTheme.accent(c));
        b.setTextSize(15);
        b.setBackground(CaseViews.box(c, Color.TRANSPARENT, 10, AppTheme.accent(c), 1));
        return b;
    }

    /** Date picker writing dd/MM/yyyy into the field. */
    static void pickDmy(Context c, EditText target) {
        Calendar cal = Calendar.getInstance();
        Date d = DiaryStore.parse(target.getText().toString());
        if (d != null) cal.setTime(d);
        new DatePickerDialog(c, (view, y, m, day) -> {
            Calendar x = Calendar.getInstance();
            x.set(y, m, day);
            target.setText(DiaryStore.fmt(x.getTime()));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    /** Read-only field, tap -> dd/MM/yyyy picker. */
    static EditText dmyInput(Context c, LinearLayout f, String label, String value) {
        EditText e = input(c, f, label, value, InputType.TYPE_NULL);
        e.setFocusable(false);
        e.setOnClickListener(v -> pickDmy(c, e));
        return e;
    }
}
