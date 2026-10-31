package com.example.twobuttons;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The "Edit document" box (Document name, Category, Stage, Attach to matter) shared by the Document Vault
 * cards and the Category marker viewer. The real file and its name on disk are never touched.
 */
final class DocEdit {
    private DocEdit() {}

    /** The uploaded-document record behind this file, or null for a plain marked PDF. */
    static LegalStore.Doc docFor(Activity a, String path) {
        try {
            String uri = PhotoFlow.uri(a, new File(path)).toString();
            for (LegalStore.Doc d : new LegalStore(a).docs()) {
                if (uri.equals(d.uri) || d.more.contains(uri)) return d;
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** Matter shown to the user: never empty, never "[]". */
    static String matterOf(Activity a, String path) {
        String m = a.getSharedPreferences("pdf_matters", Context.MODE_PRIVATE).getString(path, "");
        if (m.isEmpty()) {
            LegalStore.Doc d = docFor(a, path);
            if (d != null) m = d.caseName;
        }
        m = m == null ? "" : m.trim();
        if (m.isEmpty() || m.equals("[]") || m.equals(MatterPicker.VAULT)) return "General Document Vault";
        return m;
    }


    /** Marker only: one row per page range, each with its own category. Reopening shows the current ranges. */
    static final class Ranges {
        private static final class Row { android.widget.EditText pg; String cat = ""; View card; }
        private final Activity a;
        private final LinearLayout box;
        private final java.util.List<Row> rows = new ArrayList<>();
        private final int pc;

        Ranges(Activity a, LinearLayout form, Map<Integer, String> marks, int pc) {
            this.a = a;
            this.pc = pc;
            LegalUi.label(a, form, "Pages and categories");
            box = new LinearLayout(a);
            box.setOrientation(LinearLayout.VERTICAL);
            form.addView(box);
            if (marks.isEmpty()) {
                addRow(pc > 1 ? "1-" + pc : "1", "");          // default: the whole PDF, just pick a category
            } else {
                java.util.Map<String, java.util.List<Integer>> by = new java.util.LinkedHashMap<>();
                for (Integer p : new java.util.TreeSet<>(marks.keySet())) {
                    String c = marks.get(p);
                    if (!by.containsKey(c)) by.put(c, new ArrayList<Integer>());
                    by.get(c).add(p);
                }
                for (Map.Entry<String, java.util.List<Integer>> e : by.entrySet()) addRow(format(e.getValue()), e.getKey());
            }
            TextView add = CaseViews.text(a, "+ Add another range", 13, IconPalette.pick(a, 0xFF1A237E, 0xFFFFD54F), true);
            add.setPadding(0, CaseViews.dp(a, 8), 0, CaseViews.dp(a, 2));
            add.setOnClickListener(v -> addRow("", ""));
            form.addView(add);
            TextView note = CaseViews.text(a, "Each range gets its own category. Pages left out stay unmarked. "
                    + "Example: 1-3 Petition, 4-6 Evidence.", 11, AppTheme.sub(a), false);
            note.setPadding(0, CaseViews.dp(a, 2), 0, CaseViews.dp(a, 2));
            form.addView(note);
        }

        private void addRow(String pages, String cat) {
            final Row r = new Row();
            r.cat = cat;
            LinearLayout card = new LinearLayout(a);
            card.setOrientation(LinearLayout.VERTICAL);
            int pad = CaseViews.dp(a, 8);
            card.setPadding(pad, pad, pad, pad);
            card.setBackground(CaseViews.box(a, 0x00000000, 10, AppTheme.sub(a) & 0x55FFFFFF, 1));
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cl.bottomMargin = CaseViews.dp(a, 6);
            r.pg = LegalUi.text(a, card, "Pages (1 to " + pc + ", e.g. 1-3, 5)", pages);
            LegalUi.label(a, card, "Category");
            final TextView[] tv = new TextView[1];
            tv[0] = FieldStyle.dropdown(a, card, "Select category", v ->
                    CategoryPicker.pick(a, r.cat, s -> {
                        r.cat = s;
                        FieldStyle.setDropdownValue(tv[0], s, "Select category");
                    }), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            FieldStyle.setDropdownValue(tv[0], cat, "Select category");
            TextView rm = CaseViews.text(a, "Remove range", 12, 0xFFD32F2F, true);
            rm.setPadding(0, CaseViews.dp(a, 6), 0, 0);
            rm.setOnClickListener(v -> {
                if (rows.size() <= 1) {
                    android.widget.Toast.makeText(a, "At least one range is needed", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                rows.remove(r);
                box.removeView(r.card);
            });
            card.addView(rm);
            r.card = card;
            rows.add(r);
            box.addView(card, cl);
        }

        /** "1-3, 6" for 0-based pages. */
        static String format(java.util.List<Integer> pages) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            while (i < pages.size()) {
                int j = i;
                while (j + 1 < pages.size() && pages.get(j + 1) == pages.get(j) + 1) j++;
                if (sb.length() > 0) sb.append(", ");
                sb.append(pages.get(i) + 1);
                if (j > i) sb.append('-').append(pages.get(j) + 1);
                i = j + 1;
            }
            return sb.toString();
        }

        private java.util.List<Integer> parse(String txt) {
            java.util.TreeSet<Integer> out = new java.util.TreeSet<>();
            for (String part : txt.split("[,;\\s]+")) {
                if (part.isEmpty()) continue;
                try {
                    int x, y, dash = part.indexOf('-');
                    if (dash > 0) {
                        x = Integer.parseInt(part.substring(0, dash).trim());
                        y = Integer.parseInt(part.substring(dash + 1).trim());
                    } else {
                        x = y = Integer.parseInt(part.trim());
                    }
                    if (x > y) { int t = x; x = y; y = t; }
                    for (int p = Math.max(1, x); p <= Math.min(pc, y); p++) out.add(p - 1);
                } catch (NumberFormatException ignored) { }
            }
            return new ArrayList<>(out);
        }

        /** null when fine, else the message to show. */
        String validate() {
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                if (parse(r.pg.getText().toString()).isEmpty()) return "Range " + (i + 1) + ": enter pages like 1-3, 5";
                if (r.cat.trim().isEmpty()) return "Range " + (i + 1) + ": select a category";
            }
            return null;
        }

        /** Replaces every page mark of the file by these ranges (a later range wins an overlap). Returns the document category. */
        String apply(String path, MarkStore store) {
            store.clear(path);
            Set<String> used = new TreeSet<>();
            for (Row r : rows) {
                String c = r.cat.trim();
                for (int p : parse(r.pg.getText().toString())) store.set(path, p, c);
            }
            for (String c : store.all(path).values()) used.add(c);
            return used.size() == 1 ? used.iterator().next() : "Multiple";
        }
    }
    /** Page count of a PDF (0 when it cannot be read). */
    static int pageCount(File f) {
        try (android.os.ParcelFileDescriptor pfd = android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
             android.graphics.pdf.PdfRenderer r = new android.graphics.pdf.PdfRenderer(pfd)) {
            return r.getPageCount();
        } catch (Exception e) {
            return 0;
        }
    }

    static void show(final Activity a, final String path, final Runnable after) {
        show(a, path, after, null);
    }

    /** movedTo gets the file's new path when saving moved it into the app's Documents folder. */
    static void show(final Activity a, final String path, final Runnable after, final java.util.function.Consumer<String> movedTo) {
        final File f = new File(path);
        final MarkStore store = new MarkStore(a);
        final Map<Integer, String> marks = store.all(path);
        final LegalStore ls = new LegalStore(a);
        final LegalStore.Doc cur = docFor(a, path);
        final String ext = DocFileCard.ext(f.getName()).isEmpty() ? "" : "." + DocFileCard.ext(f.getName());
        final SharedPreferences mp = a.getSharedPreferences("pdf_matters", Context.MODE_PRIVATE);
        String savedMatter = matterOf(a, path);

        LinearLayout form = LegalUi.form(a);
        String shown = cur == null || cur.name.isEmpty() ? f.getName() : cur.name;
        final EditText name = LegalUi.text(a, form, "Document name / caption", DocFileCard.stem(shown));

        final boolean pdf = f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
        // an unmarked image / Word file is one page: its first mark goes on page 0
        final java.util.List<Integer> pages = new ArrayList<>(marks.keySet());
        if (pages.isEmpty() && !pdf) pages.add(0);

        final Set<String> cats = new TreeSet<>(marks.values());
        final CategoryPicker cp;
        final Ranges rr;
        final String[] multiNew = {null};      // several categories: one category picked here is set on ALL marked pages
        if (a instanceof MarkActivity && pdf) {
            // opened from the marker (direct share or reopened PDF): page ranges, each with its own category
            cp = null;
            rr = new Ranges(a, form, marks, pageCount(f));
        } else if (cats.size() <= 1) {
            rr = null;
            cp = new CategoryPicker(a, form, cats.isEmpty() ? "" : cats.iterator().next());
        } else {
            cp = null;
            rr = null;
            final String multiLabel = "Multiple (" + cats.size() + " categories)";
            LegalUi.label(a, form, "Category");
            final TextView[] mtv = new TextView[1];
            mtv[0] = FieldStyle.dropdown(a, form, multiLabel, v ->
                    CategoryPicker.pick(a, multiNew[0] == null ? "" : multiNew[0], s -> {
                        multiNew[0] = s;
                        FieldStyle.setDropdownValue(mtv[0], s + "  (all " + marks.size() + " marked pages)", multiLabel);
                    }), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            FieldStyle.setDropdownValue(mtv[0], multiLabel, multiLabel);
            TextView note = CaseViews.text(a, "Tap to give every marked page ONE category. Leave it as is to keep "
                    + "the different page categories (single pages are changed in the viewer / Show details).",
                    11, AppTheme.sub(a), false);
            note.setPadding(0, CaseViews.dp(a, 4), 0, CaseViews.dp(a, 2));
            form.addView(note);
        }
        final MatterPicker mpk = new MatterPicker(a, form, savedMatter, cur == null ? "" : cur.stage, pdf);

        LegalUi.formDialog(a, "Edit document", form, () -> {
            if (!LegalUi.need(a, name, "Document name")) return false;
            if (rr != null) {
                String err = rr.validate();
                if (err != null) {
                    android.widget.Toast.makeText(a, err, android.widget.Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
            if (marks.isEmpty() && !pdf && cp != null && cp.get().trim().isEmpty()) {
                android.widget.Toast.makeText(a, "Select a category", android.widget.Toast.LENGTH_SHORT).show();
                return false;
            }
            if (mpk.isCreateNew()) {
                // attach this file to a NEW case: same route as the marker's old "Create new case"
                if (rr != null) {
                    rr.apply(path, store);
                } else if (cp != null) {
                    String neu = cp.get();
                    for (Integer pg : pages) store.set(path, pg, neu);
                } else if (multiNew[0] != null) {
                    for (Integer pg : pages) store.set(path, pg, multiNew[0]);
                }
                android.content.Intent nc = new android.content.Intent(a, NewCaseActivity.class);
                nc.putExtra(NewCaseActivity.EXTRA_UPLOAD_PDF, path);
                nc.putExtra(NewCaseActivity.EXTRA_UPLOAD_MATTER, MatterPicker.CREATE_NEW);
                a.startActivity(nc);
                if (a instanceof MarkActivity) a.finish();      // no stale marker behind the New Case popup
                else if (after != null) after.run();
                return true;
            }
            LegalStore.Doc d = cur;
            String newName = LegalUi.str(name);
            String stored = DocFileCard.ext(newName).isEmpty() ? newName + ext : newName;
            // a file that is not in the app's Documents folder yet is moved in now; its page marks follow
            String curPath = path;
            File ff = f;
            if (d == null) {
                File moved = DataFolder.moveIntoDocuments(a, f);
                if (moved != null && !moved.getAbsolutePath().equals(path)) {
                    store.move(a, path, moved.getAbsolutePath());
                    curPath = moved.getAbsolutePath();
                    ff = moved;
                }
            }
            if (d == null) {
                // a plain marked PDF has no document record yet: create one so name and stage can be kept
                d = new LegalStore.Doc();
                d.date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(new java.util.Date());
                d.size = android.text.format.Formatter.formatShortFileSize(a, ff.length());
                d.fileName = ff.getName();
                try { d.uri = PhotoFlow.uri(a, ff).toString(); } catch (Exception ignored) { }
            }
            d.name = stored;
            d.caseName = mpk.matter();
            d.stage = mpk.stage();
            // a PDF with no page marked yet: the chosen category is put on EVERY page, no range asked
            java.util.List<Integer> targets = pages;
            if (pdf && targets.isEmpty()) {
                targets = new ArrayList<>();
                int pc = pageCount(ff);
                for (int pg = 0; pg < pc; pg++) targets.add(pg);
            }
            if (rr != null) {
                d.category = rr.apply(curPath, store);
            } else if (cp != null) {
                String neu = cp.get().trim();
                if (neu.isEmpty()) neu = "Other";
                d.category = neu;
                for (Integer pg : targets) store.set(curPath, pg, neu);
            } else if (multiNew[0] != null) {
                d.category = multiNew[0];
                for (Integer pg : targets) store.set(curPath, pg, multiNew[0]);
            } else {
                d.category = "Multiple";      // pages keep their different categories
            }
            ls.saveDoc(d);
            String m = mpk.matter();
            mp.edit().putString(curPath, m.equals("General Document Vault") ? MatterPicker.VAULT : m).apply();
            if (!curPath.equals(path) && movedTo != null) movedTo.accept(curPath);
            android.widget.Toast.makeText(a, "Saved to Document Vault", android.widget.Toast.LENGTH_SHORT).show();
            if (after != null) after.run();
            return true;
        }, null);
    }
}
