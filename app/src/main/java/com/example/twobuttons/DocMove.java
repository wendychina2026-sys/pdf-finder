package com.example.twobuttons;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Edit ONE file of a document card: change its category, stage and matter.
 * A file that no longer fits its card leaves it (nothing is deleted) and joins the card that already
 * holds that matter + category + stage, or a new card. A card's last file is simply re-labelled.
 */
final class DocMove {
    private DocMove() {}

    private static String norm(String s) { return s == null ? "" : s.trim(); }

    private static String vaultName(String m) {
        m = norm(m);
        return (m.isEmpty() || m.equals("[]") || m.equals(MatterPicker.VAULT)) ? "General Document Vault" : m;
    }

    static void show(final Activity a, final String path, final Runnable after) {
        final LegalStore ls = new LegalStore(a);
        final LegalStore.Doc doc = DocEdit.docFor(a, path);
        if (doc == null) { DocEdit.show(a, path, after); return; }      // plain marked PDF: the normal editor
        final MarkStore store = new MarkStore(a);
        final Map<Integer, String> marks = store.all(path);
        final boolean pdf = path.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");

        java.util.Set<String> cats = new java.util.TreeSet<>(marks.values());
        String curCat = cats.size() == 1 ? cats.iterator().next() : (cats.isEmpty() ? doc.category : "");

        LinearLayout form = LegalUi.form(a);
        TextView hint = CaseViews.text(a, "This MOVES the file. If its category, stage or matter differs from the card, "
                + "it moves to the matching card (or a new one). Nothing is deleted.", 11, AppTheme.sub(a), false);
        hint.setPadding(0, 0, 0, CaseViews.dp(a, 6));
        form.addView(hint);
        final CategoryPicker cp = new CategoryPicker(a, form, curCat);
        final MatterPicker mpk = new MatterPicker(a, form, DocEdit.matterOf(a, path), doc.stage, false);
        if (cats.size() > 1) {
            TextView w = CaseViews.text(a, "This PDF has pages in different categories. Picking one here sets ALL its marked pages "
                    + "to it; leave the category empty to keep the page categories.", 11, AppTheme.sub(a), false);
            w.setPadding(0, CaseViews.dp(a, 4), 0, 0);
            form.addView(w);
        }

        LegalUi.formDialog(a, "Edit file: category / stage / matter", form, () -> {
            if (!mpk.valid()) return false;
            String cat = norm(cp.get());
            final boolean keepPages = cat.isEmpty() && cats.size() > 1;
            if (cat.isEmpty() && !keepPages) cat = "Other";
            final String matter = vaultName(mpk.matter());
            final String stage = norm(mpk.stage());

            // 1. page marks of this file
            if (!keepPages) {
                List<Integer> pages = new ArrayList<>(marks.keySet());
                if (pages.isEmpty()) {
                    if (pdf) { int pc = DocEdit.pageCount(new File(path)); for (int i = 0; i < pc; i++) pages.add(i); }
                    else pages.add(0);
                }
                for (Integer pg : pages) store.set(path, pg, cat);
            }
            final String newCat = keepPages ? "Multiple" : cat;
            a.getSharedPreferences("pdf_matters", Context.MODE_PRIVATE).edit()
                    .putString(path, matter.equals("General Document Vault") ? MatterPicker.VAULT : matter).apply();

            final String uri;
            try { uri = PhotoFlow.uri(a, new File(path)).toString(); }
            catch (Exception e) { return false; }

            boolean same = vaultName(doc.caseName).equals(matter)
                    && norm(doc.stage).equals(stage) && norm(doc.category).equals(newCat);
            if (same) { done(a, after, "Nothing to move: same card"); return true; }

            boolean only = doc.more.isEmpty();
            if (only) {                                  // last file of its card: just re-label the card
                doc.category = newCat;
                doc.caseName = matter.equals("General Document Vault") ? "" : matter;
                doc.stage = stage;
                ls.saveDoc(doc);
                done(a, after, "Updated");
                return true;
            }

            // 2. take the file out of its old card
            List<Uri> rest = new ArrayList<>();
            if (!doc.uri.isEmpty() && !doc.uri.equals(uri)) rest.add(Uri.parse(doc.uri));
            for (String m : doc.more) if (!m.equals(uri)) rest.add(Uri.parse(m));
            if (!rest.isEmpty()) {
                doc.uri = rest.get(0).toString();
                doc.more.clear();
                for (int k = 1; k < rest.size(); k++) doc.more.add(rest.get(k).toString());
                doc.fileName = FileSetDialog.label(a, rest);
                doc.size = FileSetDialog.totalSize(a, rest);
                String nm = FileSetDialog.displayName(a, rest.get(0));
                int dot = nm.lastIndexOf('.');
                if (dot > 0) doc.name = DocFileCard.stem(doc.name) + nm.substring(dot);
                ls.saveDoc(doc);
            }

            // 3. put it on the card that already has this matter + category + stage, else a new card
            LegalStore.Doc target = null;
            for (LegalStore.Doc d : ls.docs()) {
                if (d.id.equals(doc.id)) continue;
                if (vaultName(d.caseName).equals(matter) && norm(d.stage).equals(stage)
                        && norm(d.category).equals(newCat) && !d.uri.isEmpty()) { target = d; break; }
            }
            List<Uri> files = new ArrayList<>();
            if (target != null) {
                files.add(Uri.parse(target.uri));
                for (String m : target.more) files.add(Uri.parse(m));
                files.add(Uri.parse(uri));
            } else {
                target = new LegalStore.Doc();
                target.date = doc.date;
                target.category = newCat;
                target.caseName = matter.equals("General Document Vault") ? "" : matter;
                target.stage = stage;
                target.name = FileSetDialog.displayName(a, Uri.parse(uri));
                files.add(Uri.parse(uri));
            }
            target.uri = files.get(0).toString();
            target.more.clear();
            for (int k = 1; k < files.size(); k++) target.more.add(files.get(k).toString());
            target.fileName = FileSetDialog.label(a, files);
            target.size = FileSetDialog.totalSize(a, files);
            ls.saveDoc(target);
            done(a, after, "File moved to its own card");
            return true;
        }, null);
    }


    /**
     * Edit ONE file and KEEP it in its card: change its name, category, stage and matter in place.
     * Nothing moves. (Stage is a card-level field, so a changed stage applies to the whole card.)
     */
    static void showHere(final Activity a, final String path, final String currentLabel, final Runnable after) {
        final LegalStore ls = new LegalStore(a);
        final LegalStore.Doc doc = DocEdit.docFor(a, path);
        if (doc == null) { DocEdit.show(a, path, after); return; }
        final MarkStore store = new MarkStore(a);
        final Map<Integer, String> marks = store.all(path);
        final boolean pdf = path.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");

        java.util.Set<String> cats = new java.util.TreeSet<>(marks.values());
        String curCat = cats.size() == 1 ? cats.iterator().next() : (cats.isEmpty() ? doc.category : "");

        LinearLayout form = LegalUi.form(a);
        TextView hint = CaseViews.text(a, "This file STAYS in its card. Only its name, category, stage and matter change. "
                + "(Stage is shared by the whole card.)", 11, AppTheme.sub(a), false);
        hint.setPadding(0, 0, 0, CaseViews.dp(a, 6));
        form.addView(hint);
        final android.widget.EditText nameEt = LegalUi.text(a, form, "File name / caption", currentLabel);
        final CategoryPicker cp = new CategoryPicker(a, form, curCat);
        final MatterPicker mpk = new MatterPicker(a, form, DocEdit.matterOf(a, path), doc.stage, false);
        if (cats.size() > 1) {
            TextView w = CaseViews.text(a, "This PDF has pages in different categories. Picking one sets ALL its marked pages "
                    + "to it; leave the category empty to keep the page categories.", 11, AppTheme.sub(a), false);
            w.setPadding(0, CaseViews.dp(a, 4), 0, 0);
            form.addView(w);
        }

        LegalUi.formDialog(a, "Edit file (stays in this card)", form, () -> {
            if (!LegalUi.need(a, nameEt, "File name")) return false;
            if (!mpk.valid()) return false;
            String cat = norm(cp.get());
            final boolean keepPages = cat.isEmpty() && cats.size() > 1;
            if (cat.isEmpty() && !keepPages) cat = "Other";
            final String matter = vaultName(mpk.matter());
            final String stage = norm(mpk.stage());

            FileCaption.set(a, path, LegalUi.str(nameEt));
            if (!keepPages) {
                List<Integer> pages = new ArrayList<>(marks.keySet());
                if (pages.isEmpty()) {
                    if (pdf) { int pc = DocEdit.pageCount(new File(path)); for (int i = 0; i < pc; i++) pages.add(i); }
                    else pages.add(0);
                }
                for (Integer pg : pages) store.set(path, pg, cat);
            }
            a.getSharedPreferences("pdf_matters", Context.MODE_PRIVATE).edit()
                    .putString(path, matter.equals("General Document Vault") ? MatterPicker.VAULT : matter).apply();

            // the card itself: only when it holds just this file do category / matter follow it;
            // with several files the card keeps its place and only the stage (card-level) is updated
            boolean only = doc.more.isEmpty();
            if (only) {
                if (!keepPages) doc.category = cat;
                doc.caseName = matter.equals("General Document Vault") ? "" : matter;
            } else if (!keepPages && !norm(doc.category).equals(cat)) {
                doc.category = "Multiple";
            }
            doc.stage = stage;
            ls.saveDoc(doc);
            done(a, after, "Saved - file stays in this card");
            return true;
        }, null);
    }

    private static void saveOrder(Activity a, LegalStore ls, LegalStore.Doc doc, List<Uri> files) {
        doc.uri = files.get(0).toString();
        doc.more.clear();
        for (int k = 1; k < files.size(); k++) doc.more.add(files.get(k).toString());
        doc.fileName = FileSetDialog.label(a, files);
        doc.size = FileSetDialog.totalSize(a, files);
        String nm = FileSetDialog.displayName(a, files.get(0));
        int dot = nm.lastIndexOf('.');
        if (dot > 0) doc.name = DocFileCard.stem(doc.name) + nm.substring(dot);
        ls.saveDoc(doc);
    }

    private static List<Uri> filesOf(LegalStore.Doc doc) {
        List<Uri> l = new ArrayList<>();
        if (!doc.uri.isEmpty()) l.add(Uri.parse(doc.uri));
        for (String m : doc.more) l.add(Uri.parse(m));
        return l;
    }

    /** Move one file of a card up (-1) or down (+1) in the card's order. */
    static boolean shift(Activity a, String path, int delta) {
        try {
            LegalStore ls = new LegalStore(a);
            LegalStore.Doc doc = DocEdit.docFor(a, path);
            if (doc == null) return false;
            List<Uri> files = filesOf(doc);
            String me = PhotoFlow.uri(a, new File(path)).toString();
            int i = -1;
            for (int k = 0; k < files.size(); k++) if (files.get(k).toString().equals(me)) i = k;
            int j = i + delta;
            if (i < 0 || j < 0 || j >= files.size()) return false;
            java.util.Collections.swap(files, i, j);
            saveOrder(a, ls, doc, files);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Take one file off its card (its marks and caption go too). The card must keep at least one file. */
    static boolean removeFromCard(Activity a, String path) {
        try {
            LegalStore ls = new LegalStore(a);
            LegalStore.Doc doc = DocEdit.docFor(a, path);
            if (doc == null) return false;
            List<Uri> files = filesOf(doc);
            String me = PhotoFlow.uri(a, new File(path)).toString();
            List<Uri> rest = new ArrayList<>();
            for (Uri u : files) if (!u.toString().equals(me)) rest.add(u);
            if (rest.isEmpty() || rest.size() == files.size()) return false;
            saveOrder(a, ls, doc, rest);
            new MarkStore(a).clear(path);
            FileCaption.set(a, path, "");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void done(Activity a, Runnable after, String msg) {
        android.widget.Toast.makeText(a, msg, android.widget.Toast.LENGTH_SHORT).show();
        if (after != null) after.run();
    }
}
