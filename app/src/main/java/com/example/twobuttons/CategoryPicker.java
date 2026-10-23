package com.example.twobuttons;

import android.app.Activity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Document Category with drill-down: pick a category; if it has sub-options (e.g. Complaint),
 * a new list opens with them; pick one and, if that has sub-options too (Return Complaint),
 * another list opens. The saved value is the path, e.g. "Complaint › Return Complaint › Sent via Speed Post".
 */
final class CategoryPicker {
    static final String SEP = " \u203a ";
    private String value;
    private TextView tv;

    /** Dropdown for the Edit document dialogs. */
    CategoryPicker(Activity a, LinearLayout f, String current) {
        value = current == null ? "" : current;
        LegalUi.label(a, f, "Category");
        tv = FieldStyle.dropdown(a, f, "Select category", v ->
                pick(a, value, s -> {
                    value = s;
                    FieldStyle.setDropdownValue(tv, value, "Select category");
                }), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        FieldStyle.setDropdownValue(tv, value, "Select category");
    }

    String get() {
        return value.isEmpty() ? "Other" : value;
    }

    /**
     * Opens the category list and, level by level, any sub-options; onDone gets the full path.
     * The cross (or back) on a sub-level goes up exactly one level; on the first list it just closes.
     * Every sub-level shows the path so far as its subtitle.
     */
    static void pick(Activity a, String current, Consumer<String> onDone) {
        final List<String> top = new ArrayList<>(Arrays.asList(LegalStore.allDocCategories()));
        final List<String> mine = custom(a);
        for (String c : mine) if (!top.contains(c)) top.add(Math.max(0, top.size() - 1), c);   // before "Other"
        final String top0 = current == null ? "" : current.split(SEP)[0];
        if (!top0.isEmpty() && !top.contains(top0)) top.add(0, top0);
        Runnable[] again = new Runnable[1];
        again[0] = () -> PickerDialogs.searchSheet(a, "Document Category", null, top, top0,
                "Add New Category", "Category Name", s -> saveCustom(a, s),
                s -> drill(a, s, onDone, again[0]), null);
        again[0].run();
    }

    private static final String PREF = "legal_diary", KEY = "custom_doc_cats";

    /** Categories the user added (kept on the device, shown before "Other"). */
    static List<String> custom(Activity a) {
        List<String> out = new ArrayList<>();
        try {
            org.json.JSONArray j = new org.json.JSONArray(
                    a.getSharedPreferences(PREF, android.content.Context.MODE_PRIVATE).getString(KEY, "[]"));
            for (int i = 0; i < j.length(); i++) out.add(j.getString(i));
        } catch (Exception ignored) { }
        return out;
    }

    private static void saveCustom(Activity a, String name) {
        List<String> l = custom(a);
        for (String x : l) if (x.equalsIgnoreCase(name)) return;
        l.add(name);
        org.json.JSONArray j = new org.json.JSONArray();
        for (String x : l) j.put(x);
        a.getSharedPreferences(PREF, android.content.Context.MODE_PRIVATE).edit().putString(KEY, j.toString()).apply();
    }

    private static void drill(Activity a, String path, Consumer<String> onDone, Runnable up) {
        String[] subs = LegalStore.docSubOptions(path);
        if (subs.length == 0) {
            onDone.accept(path);
            return;
        }
        List<String> items = Arrays.asList(subs);
        Runnable[] self = new Runnable[1];
        self[0] = () -> PickerDialogs.optionSheet(a, "Choose one", path, items, -1,
                i -> drill(a, path + SEP + items.get(i), onDone, self[0]), up);
        self[0].run();
    }
}
