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
        if (cats.size() <= 1) {
            cp = new CategoryPicker(a, form, cats.isEmpty() ? "" : cats.iterator().next());
        } else {
            cp = null;
            TextView note = CaseViews.text(a, "Category: pages are marked in several categories. "
                    + "Change them from Show details.", 12, AppTheme.sub(a), false);
            note.setPadding(0, CaseViews.dp(a, 10), 0, CaseViews.dp(a, 2));
            form.addView(note);
        }
        final MatterPicker mpk = new MatterPicker(a, form, savedMatter, cur == null ? "" : cur.stage);

        LegalUi.formDialog(a, "Edit document", form, () -> {
            if (!LegalUi.need(a, name, "Document name")) return false;
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
            ls.saveDoc(d);
            String m = mpk.matter();
            mp.edit().putString(path, m.equals("General Document Vault") ? MatterPicker.VAULT : m).apply();
            if (after != null) after.run();
            return true;
        }, null);
    }
}
