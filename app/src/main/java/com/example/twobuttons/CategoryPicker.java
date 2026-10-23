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
        List<String> top = new ArrayList<>(Arrays.asList(LegalStore.allDocCategories()));
        String top0 = current == null ? "" : current.split(SEP)[0];
        if (!top0.isEmpty() && !top.contains(top0)) top.add(0, top0);
        Runnable[] again = new Runnable[1];
        again[0] = () -> PickerDialogs.optionSheet(a, "Document Category", null, top, top.indexOf(top0),
                i -> drill(a, top.get(i), onDone, again[0]));
        again[0].run();
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
