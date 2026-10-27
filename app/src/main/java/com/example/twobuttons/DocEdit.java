package com.example.twobuttons;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.EditText;
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

    static void show(final Activity a, final String path, final Runnable after) {
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

        final Set<String> cats = new TreeSet<>(marks.values());
        final CategoryPicker cp;
        // several categories: page -> category edits are staged here and written only on Save
        final Map<Integer, String> work = new java.util.HashMap<>(marks);
        if (cats.size() <= 1) {
            cp = new CategoryPicker(a, form, cats.isEmpty() ? "" : cats.iterator().next());
        } else {
            cp = null;
            LegalUi.label(a, form, "Category");
            final TextView[] mtv = new TextView[1];
            final Runnable relabel = () -> {
                String l = multiLabel(work);
                FieldStyle.setDropdownValue(mtv[0], l, l);
            };
            mtv[0] = FieldStyle.dropdown(a, form, multiLabel(work), v ->
                    showMulti(a, work, relabel), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            relabel.run();
            TextView note = CaseViews.text(a, "Tap to see which category each page has. Change a single page, "
                    + "or give all marked pages ONE category.",
                    11, AppTheme.sub(a), false);
            note.setPadding(0, CaseViews.dp(a, 4), 0, CaseViews.dp(a, 2));
            form.addView(note);
        }
        final boolean pdf = f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
        final MatterPicker mpk = new MatterPicker(a, form, savedMatter, cur == null ? "" : cur.stage, pdf);

        LegalUi.formDialog(a, "Edit document", form, () -> {
            if (!LegalUi.need(a, name, "Document name")) return false;
            if (mpk.isCreateNew()) {
                // attach this file to a NEW case: same route as the marker's old "Create new case"
                if (cp != null) {
                    String neu = cp.get();
                    for (Integer pg : new ArrayList<>(marks.keySet())) store.set(path, pg, neu);
                } else {
                    applyWork(store, path, marks, work);
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
            if (d == null) {
                // a plain marked PDF has no document record yet: create one so name and stage can be kept
                d = new LegalStore.Doc();
                d.date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(new java.util.Date());
                d.size = android.text.format.Formatter.formatShortFileSize(a, f.length());
                d.fileName = f.getName();
                try { d.uri = PhotoFlow.uri(a, f).toString(); } catch (Exception ignored) { }
            }
            d.name = stored;
            d.caseName = mpk.matter();
            d.stage = mpk.stage();
            if (cp != null) {
                String neu = cp.get();
                d.category = neu;
                for (Integer pg : new ArrayList<>(marks.keySet())) store.set(path, pg, neu);
            }
            if (cp == null) {
                applyWork(store, path, marks, work);
                Set<String> now = new TreeSet<>(work.values());
                if (now.size() == 1) d.category = now.iterator().next();
            }
            ls.saveDoc(d);
            String m = mpk.matter();
            mp.edit().putString(path, m.equals("General Document Vault") ? MatterPicker.VAULT : m).apply();
            if (after != null) after.run();
            return true;
        }, null);
    }

    private static String multiLabel(Map<Integer, String> work) {
        Set<String> c = new TreeSet<>(work.values());
        return c.size() == 1 ? c.iterator().next() : "Multiple (" + c.size() + " categories)";
    }

    private static void applyWork(MarkStore store, String path, Map<Integer, String> marks, Map<Integer, String> work) {
        for (Map.Entry<Integer, String> e : work.entrySet()) {
            if (!e.getValue().equals(marks.get(e.getKey()))) store.set(path, e.getKey(), e.getValue());
        }
    }

    /** Lists every marked page with its current category; tap a page to change it, or set ONE category for all. */
    private static void showMulti(final Activity a, final Map<Integer, String> work, final Runnable onClose) {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(CaseViews.dp(a, 16), CaseViews.dp(a, 8), CaseViews.dp(a, 16), CaseViews.dp(a, 8));
        final Runnable[] redraw = new Runnable[1];
        redraw[0] = () -> {
            box.removeAllViews();
            java.util.List<Integer> pgs = new ArrayList<>(work.keySet());
            java.util.Collections.sort(pgs);
            for (final int pg : pgs) {
                LinearLayout row = new LinearLayout(a);
                row.setOrientation(LinearLayout.VERTICAL);
                int pd = CaseViews.dp(a, 10);
                row.setPadding(pd, pd, pd, pd);
                row.setBackground(CaseViews.box(a, AppTheme.card(a), 12, AppTheme.cardStroke(a), 1));
                row.addView(CaseViews.text(a, "Page " + (pg + 1), 12, AppTheme.sub(a), true));
                row.addView(CaseViews.text(a, work.get(pg), 14, AppTheme.text(a), true));
                row.setOnClickListener(v -> CategoryPicker.pick(a, work.get(pg), neu -> {
                    work.put(pg, neu);
                    redraw[0].run();
                }, new java.util.HashMap<>(work), null, true));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin = CaseViews.dp(a, 6);
                box.addView(row, lp);
            }
        };
        redraw[0].run();
        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(box);
        final android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(a)
                .setTitle("Marked pages")
                .setView(sv)
                .setPositiveButton("Done", null)
                .setNeutralButton("Same for all", null)
                .create();
        dlg.setOnDismissListener(d -> onClose.run());
        dlg.show();
        dlg.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                CategoryPicker.pick(a, "", s -> new android.app.AlertDialog.Builder(a)
                        .setTitle("Same category for all?")
                        .setMessage("All " + work.size() + " marked pages will be set to \"" + s + "\".")
                        .setPositiveButton("Set all", (d, w) -> {
                            for (Integer k : new ArrayList<>(work.keySet())) work.put(k, s);
                            redraw[0].run();
                        })
                        .setNegativeButton("Cancel", null)
                        .show()));
    }
}
