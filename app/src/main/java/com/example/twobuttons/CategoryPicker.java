package com.example.twobuttons;

import android.app.Activity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
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
        pick(a, current, onDone, null);
    }

    /**
     * Same picker; marked (page index 0-based -> saved category path, may be null) adds a small
     * "Marked on pages ..." line under every category/option that already has marked pages.
     * Used by the Category marker only.
     */
    static void pick(Activity a, String current, Consumer<String> onDone, Map<Integer, String> marked) {
        pick(a, current, onDone, marked, null);
    }

    /** As above, plus edit-marked-pages / rename / delete icons on the first list (manage may be null). */
    static void pick(Activity a, String current, Consumer<String> onDone, Map<Integer, String> marked,
                     PickerDialogs.Manage manage) {
        final List<String> top = new ArrayList<>(Arrays.asList(LegalStore.allDocCategories()));
        final List<String> mine = custom(a);
        for (String c : mine) if (!top.contains(c)) top.add(Math.max(0, top.size() - 1), c);   // before "Other"
        final String top0 = current == null ? "" : current.split(SEP)[0];
        if (!top0.isEmpty() && !top.contains(top0)) top.add(0, top0);
        Runnable[] again = new Runnable[1];
        again[0] = () -> PickerDialogs.searchSheet(a, "Document Category", null, top, top0,
                "Add New Category", "Category Name", s -> saveCustom(a, s),
                s -> drill(a, s, onDone, again[0], marked), null,
                marked == null ? null : item -> pagesNote(marked, item), manage);
        again[0].run();
    }

    /** Pages (0-based) of marked whose saved path is cat or starts with cat › . */
    private static List<Integer> pagesOf(Map<Integer, String> marked, String cat) {
        List<Integer> out = new ArrayList<>();
        for (Map.Entry<Integer, String> e : marked.entrySet()) {
            String v = e.getValue();
            if (v != null && (v.equals(cat) || v.startsWith(cat + SEP))) out.add(e.getKey());
        }
        java.util.Collections.sort(out);
        return out;
    }

    /**
     * Edit / rename / delete tools for the Category list, tied to one PDF's marks.
     * Edit marked pages: change the category of each page marked under a category (asks first).
     * Rename / delete: only categories the user added; both ask first.
     */
    static PickerDialogs.Manage manage(Activity a, String pdfPath, Map<Integer, String> marks) {
        final MarkStore ms = new MarkStore(a);
        return new PickerDialogs.Manage() {
            @Override public boolean hasPages(String item) { return !pagesOf(marks, item).isEmpty(); }
            @Override public boolean editable(String item) { return custom(a).contains(item); }

            @Override public void editPages(String item, Runnable refresh) {
                final Runnable[] redraw = new Runnable[1];
                redraw[0] = PickerDialogs.editRowsSheet(a, "Marked pages",
                        item + ". Tap a page to change its category.",
                        () -> {
                            List<String[]> rows = new ArrayList<>();
                            for (int pg : pagesOf(marks, item)) rows.add(new String[]{"Page " + (pg + 1), marks.get(pg)});
                            return rows;
                        }, "No pages are marked with this category now.",
                        i -> {
                            final int pg = pagesOf(marks, item).get(i);
                            final String now = marks.get(pg);
                            pick(a, now, neu -> {
                                if (neu.equals(now)) return;
                                PickerDialogs.confirm(a, "Change mark?", "Page " + (pg + 1) + " is marked as \""
                                        + now + "\". Change it to \"" + neu + "\"?", "Change", () -> {
                                    ms.set(pdfPath, pg, neu);
                                    marks.put(pg, neu);
                                    redraw[0].run();
                                    refresh.run();
                                });
                            }, new java.util.HashMap<>(marks));
                        });
            }

            @Override public void rename(String old, String neu) {
                renameCustom(a, old, neu);
                ms.renameCategory(old, neu);
                for (Map.Entry<Integer, String> e : marks.entrySet()) {
                    String v = e.getValue();
                    if (v.equals(old)) e.setValue(neu);
                    else if (v.startsWith(old + SEP)) e.setValue(neu + v.substring(old.length()));
                }
            }

            @Override public void delete(String item) {
                deleteCustom(a, item);
                ms.removeCategory(pdfPath, item);
                marks.values().removeIf(v -> v.equals(item) || v.startsWith(item + SEP));
            }

            @Override public String deleteNote(String item) {
                int n = pagesOf(marks, item).size();
                return n == 0 ? "" : n + (n == 1 ? " page" : " pages")
                        + " of this PDF marked with it will be unmarked.";
            }
        };
    }

    private static void renameCustom(Activity a, String old, String neu) {
        List<String> l = custom(a);
        int i = l.indexOf(old);
        if (i < 0) return;
        l.set(i, neu);
        writeCustom(a, l);
    }

    private static void deleteCustom(Activity a, String name) {
        List<String> l = custom(a);
        if (l.remove(name)) writeCustom(a, l);
    }

    private static void writeCustom(Activity a, List<String> l) {
        org.json.JSONArray j = new org.json.JSONArray();
        for (String x : l) j.put(x);
        a.getSharedPreferences(PREF, android.content.Context.MODE_PRIVATE).edit().putString(KEY, j.toString()).apply();
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

    /** "Marked on pages 1, 3-5" for pages whose saved path is prefix or starts with prefix + SEP. */
    private static String pagesNote(Map<Integer, String> marked, String prefix) {
        TreeSet<Integer> pages = new TreeSet<>();
        for (Map.Entry<Integer, String> e : marked.entrySet()) {
            String v = e.getValue();
            if (v != null && (v.equals(prefix) || v.startsWith(prefix + SEP))) pages.add(e.getKey() + 1);
        }
        if (pages.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        Integer start = null, prev = null;
        for (int p : pages) {
            if (start == null) { start = p; prev = p; continue; }
            if (p == prev + 1) { prev = p; continue; }
            sb.append(sb.length() > 0 ? ", " : "").append(start.equals(prev) ? "" + start : start + "-" + prev);
            start = p; prev = p;
        }
        sb.append(sb.length() > 0 ? ", " : "").append(start.equals(prev) ? "" + start : start + "-" + prev);
        return (pages.size() == 1 ? "Marked on page " : "Marked on pages ") + sb;
    }

    private static void drill(Activity a, String path, Consumer<String> onDone, Runnable up,
                              Map<Integer, String> marked) {
        String[] subs = LegalStore.docSubOptions(path);
        if (subs.length == 0) {
            onDone.accept(path);
            return;
        }
        List<String> items = Arrays.asList(subs);
        Runnable[] self = new Runnable[1];
        self[0] = () -> PickerDialogs.optionSheet(a, "Choose one", path, items, -1,
                i -> drill(a, path + SEP + items.get(i), onDone, self[0], marked), up,
                marked == null ? null : item -> pagesNote(marked, path + SEP + item));
        self[0].run();
    }
}
