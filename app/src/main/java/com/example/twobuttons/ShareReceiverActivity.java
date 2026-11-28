package com.example.twobuttons;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/** Receives PDFs from the system Share / Open-with menu, copies the first one
 *  into app storage and opens it in the Category marker. */
public class ShareReceiverActivity extends Activity {

    private final PhotoFlow flow = new PhotoFlow(this, 64);
    private static final int REQ_PICK_DOC = 81;
    private ArrayList<File> pendingFiles;
    private boolean pendingPdf;
    private String pendingName;       // typed after Confirm: this file's name
    private String pendingStage;      // ... and its stage; a different stage puts the file on the card with that stage
    private String pendingCat;        // ... and its category (images / Word); a PDF is marked in the marker

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PICK_DOC) {
            // Document Vault pick mode: OK = the chosen card, Cancel = back to the shared-file window
            final ArrayList<File> files = pendingFiles;
            if (files == null) { finish(); return; }
            LegalStore.Doc chosen = null;
            String id = data == null ? null : data.getStringExtra("doc_id");
            if (res == RESULT_OK && id != null) {
                for (LegalStore.Doc d : new LegalStore(this).docs()) if (id.equals(d.id)) { chosen = d; break; }
            }
            if (chosen != null) askDetails(files, chosen, pendingPdf);
            else askNewOrAttach(files, pendingPdf);
            return;
        }
        if (FileSetDialog.handle(this, req, res, data)) return;
        flow.handle(req, res);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }

    private void handle(Intent in) {
        ArrayList<Uri> uris = new ArrayList<>();
        String action = in == null ? null : in.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri u = in.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) uris.add(u);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> l = in.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (l != null) uris.addAll(l);
        } else if (Intent.ACTION_VIEW.equals(action) && in.getData() != null) {
            uris.add(in.getData());
        }

        if (uris.isEmpty()) {
            Toast.makeText(this, "No file received", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        File dir = DataFolder.dir("Documents", new File(getExternalFilesDir(null), "shared"));
        if (!dir.exists()) dir.mkdirs();

        File first = null;          // first PDF -> Category marker
        File firstWord = null;      // first Word file -> Upload Legal Document popup
        final ArrayList<File> words = new ArrayList<>();   // every shared image / Word file
        final ArrayList<File> pdfs = new ArrayList<>();    // every shared PDF
        final ArrayList<File> all = new ArrayList<>();     // everything shared, in the order it came
        int copied = 0, wordCount = 0;
        for (Uri u : uris) {
            String type = getContentResolver().getType(u);
            String nm = u.getLastPathSegment() == null ? "" : u.getLastPathSegment().toLowerCase(java.util.Locale.ROOT);
            boolean word = (type != null && (type.equals("application/msword") || type.contains("wordprocessingml")))
                    || nm.endsWith(".doc") || nm.endsWith(".docx");
            boolean image = (type != null && type.startsWith("image/"))
                    || nm.matches(".*\\.(jpe?g|png|webp|gif|bmp|heic|heif)$");
            word = word || image;       // an image, like a Word file, has no pages to mark
            File f = DataFolder.localOriginal(this, u);     // reachable file: used in place, moved when saved
            if (f == null) {
                f = word ? DataFolder.importFile(this, u, dir) : DataFolder.importPdf(this, u, dir);
                if (f != null) DataFolder.registerOriginal(this, u, f);
            }
            if (f == null) continue;
            all.add(f);
            if (word) {
                wordCount++;
                words.add(f);
                if (firstWord == null) firstWord = f;
            } else {
                copied++;
                pdfs.add(f);
                if (first == null) first = f;
            }
        }

        final File first0 = first, firstWord0 = firstWord;
        final int copied0 = copied, wordCount0 = wordCount;
        final Runnable normal = () -> {
            if (first0 == null && firstWord0 == null) {
                Toast.makeText(this, "Cannot read the shared file", Toast.LENGTH_LONG).show();
            } else if (first0 != null) {
                if (copied0 > 1) {
                    Toast.makeText(this, copied0 + " PDFs saved. Opening the first.",
                            Toast.LENGTH_LONG).show();
                }
                if (wordCount0 > 0) {
                    Toast.makeText(this, wordCount0 + " file(s) saved in the Documents folder.",
                            Toast.LENGTH_LONG).show();
                }
                // new document (page marker) or attach to a document already in the Vault
                askNewOrAttach(pdfs, true);
                return;                     // finishes after the choice
            } else {
                if (wordCount0 > 1) {
                    Toast.makeText(this, wordCount0 + " files saved. All of them open in one upload box.",
                            Toast.LENGTH_LONG).show();
                }
                // a Word file or image has no pages to mark: new document, or attach to a document already in the Vault
                askNewOrAttach(words, false);
                return;                     // finishes after the choice
            }
            finish();
        };
        // shared through the "(Batch Mode)" entry: one PDF with several pages counts as a batch too
        final boolean viaBatch = in != null && in.getComponent() != null
                && in.getComponent().getClassName().endsWith("ShareReceiverBatch");
        if (all.size() > 1 || (viaBatch && all.size() == 1 && MultiDocDetector.totalPages(all) > 1)) {
            // more than one file (or one multi-page PDF via Batch Mode): wait for the user to confirm, then details are set per file
            MultiDocDetector.confirm(this, all.size(), MultiDocDetector.totalPages(all),
                    () -> {
                        final boolean allPdf = pdfs.size() == all.size();
                        askMulti(all, allPdf);             // same four-choice window as for a single file
                    }, normal, this::finish);
            return;
        }
        normal.run();
    }

    /** Category marker: the shared PDF becomes a NEW document, pages marked there. */
    private void openMarker(File f) {
        Intent i = new Intent(this, MarkActivity.class);
        i.putExtra("path", f.getAbsolutePath());
        startActivity(i);
    }

    /** Upload Legal Document popup: the shared files become ONE new document (all of them, in the order shared). */
    private void openNew(ArrayList<File> files) { openNew(files, null); }

    private void openNew(ArrayList<File> files, String matter) {
        openNew(files, matter, MultiDocDetector.NO);
    }

    private void openNew(ArrayList<File> files, String matter, int multiMode) {
        openNew(files, matter, multiMode, null);
    }

    /** genCat (e.g. MCD) = the saved General card category the new card goes under; null = ask as usual. */
    private void openNew(ArrayList<File> files, String matter, int multiMode, String genCat) {
        Intent i = new Intent(this, NewCaseActivity.class);
        if (genCat != null && !genCat.isEmpty()) i.putExtra(NewCaseActivity.EXTRA_UPLOAD_GENCAT, genCat);
        i.putExtra(MultiDocDetector.EXTRA_MODE, multiMode);
        i.putExtra(NewCaseActivity.EXTRA_UPLOAD_PDF, files.get(0).getAbsolutePath());
        if (files.size() > 1) {
            ArrayList<String> rest = new ArrayList<>();
            for (int k = 1; k < files.size(); k++) rest.add(files.get(k).getAbsolutePath());
            i.putStringArrayListExtra(NewCaseActivity.EXTRA_UPLOAD_EXTRA, rest);
        }
        if (matter != null) i.putExtra(NewCaseActivity.EXTRA_UPLOAD_MATTER, matter);
        startActivity(i);
    }

    /** Saved documents the shared files can join: any saved document, PDF or not (images / Word files can be attached to a PDF card too). */
    private java.util.List<LegalStore.Doc> attachableDocs(boolean pdfShare) {
        java.util.List<LegalStore.Doc> out = new ArrayList<>();
        for (LegalStore.Doc d : new LegalStore(this).docs()) {
            if (d.uri == null || d.uri.isEmpty()) continue;
            out.add(d);
        }
        return out;
    }

    private static final String[] FOUR = {
            "New General card",
            "New card in a saved category",
            "Attach to a saved General card",
            "New case",
            "Attach to a saved case"};

    /** General Document Vault cards that shared files can join. */
    private boolean hasGeneralDocs() {
        for (LegalStore.Doc d : attachableDocs(true)) {
            String m = d.caseName == null ? "" : d.caseName.trim();
            if (m.isEmpty() || m.equals("[]") || m.equals(MatterPicker.VAULT) || m.equals("General Document Vault")) return true;
        }
        return false;
    }

    /** Saved cases (never the Vault). */
    private java.util.List<String> savedCases() {
        java.util.List<String> out = new ArrayList<>();
        for (DiaryStore.HCase hc : new DiaryStore(this).cases()) {
            String t = hc.title();
            if (t != null && !t.trim().isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    /** Saved General card category names (e.g. MCD): the saved list, plus any name already on a General card. */
    private java.util.List<String> savedGenCats() {
        java.util.List<String> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String n : new GeneralCatStore(this).list()) {
            if (n != null && !n.trim().isEmpty() && seen.add(n.trim().toLowerCase(java.util.Locale.ROOT))) out.add(n.trim());
        }
        for (LegalStore.Doc d : new LegalStore(this).docs()) {
            String n = d.genCat == null ? "" : d.genCat.trim();
            if (!n.isEmpty() && seen.add(n.toLowerCase(java.util.Locale.ROOT))) out.add(n);
        }
        return out;
    }

    /** New card filed straight under a saved General card category: the category is not asked again. */
    private void pickGenCat(final ArrayList<File> files, final boolean pdf, final int multiMode) {
        final java.util.List<String> cats = savedGenCats();
        final Runnable back = () -> { if (multiMode == MultiDocDetector.YES) askMulti(files, pdf); else askNewOrAttach(files, pdf); };
        if (cats.isEmpty()) { noneSaved("category", back); return; }
        PickerDialogs.optionSheet(this, "Which category?", "A new General card is made under it",
                cats, -1, which -> {
                    String cat = cats.get(which);
                    if (multiMode == MultiDocDetector.NO && pdf && files.size() == 1) {
                        // the marker opens as for a new General card; its Edit box already shows this category
                        getSharedPreferences("pdf_gen_cat", MODE_PRIVATE).edit()
                                .putString(files.get(0).getAbsolutePath(), cat).apply();
                        openMarker(files.get(0));
                    } else {
                        openNew(files, null, multiMode, cat);
                    }
                    finish();
                }, back);
    }

    /** One document: 1 New General card, 2 New card in saved category, 3 Attach to saved General card, 4 New case, 5 Attach to saved case. */
    private void askNewOrAttach(final ArrayList<File> files, final boolean pdf) {
        PickerDialogs.optionSheet(this, "Shared file",
                files.size() + (files.size() == 1 ? " file" : " files"), java.util.Arrays.asList(FOUR), -1, which -> {
                    if (which == 0) {
                        if (pdf) openMarker(files.get(0)); else openNew(files);
                        finish();
                    } else if (which == 1) {
                        pickGenCat(files, pdf, MultiDocDetector.NO);
                    } else if (which == 2) {
                        if (!hasGeneralDocs()) { noneSaved("General card", () -> askNewOrAttach(files, pdf)); return; }
                        pickDocument(files, pdf);
                    } else if (which == 3) {
                        // New Case page with the files waiting in the upload box until the case is created
                        openNew(files, MatterPicker.CREATE_NEW);
                        finish();
                    } else {
                        pickCase(files, pdf, MultiDocDetector.NO);
                    }
                }, this::finish);
    }

    /** Several files, "per file" confirmed: the same four choices; each file keeps its own details. */
    private void askMulti(final ArrayList<File> files, final boolean allPdf) {
        PickerDialogs.optionSheet(this, "Shared files", files.size() + " files", java.util.Arrays.asList(FOUR), -1, which -> {
            if (which == 0) {
                openNew(files, null, MultiDocDetector.YES);
                finish();
            } else if (which == 1) {
                pickGenCat(files, allPdf, MultiDocDetector.YES);
            } else if (which == 2) {
                if (!hasGeneralDocs()) { noneSaved("General card", () -> askMulti(files, allPdf)); return; }
                pickDocument(files, allPdf);
            } else if (which == 3) {
                openNew(files, MatterPicker.CREATE_NEW, MultiDocDetector.YES);
                finish();
            } else {
                pickCase(files, allPdf, MultiDocDetector.YES);
            }
        }, this::finish);
    }

    private void noneSaved(String what, Runnable back) {
        Toast.makeText(this, "No saved " + what + " yet", Toast.LENGTH_SHORT).show();
        back.run();
    }

    /** Own card(s) with their OWN category and stage, under a saved case. */
    private void pickCase(final ArrayList<File> files, final boolean pdf, final int multiMode) {
        final java.util.List<String> cases = savedCases();
        if (cases.isEmpty()) {
            noneSaved("case", () -> { if (multiMode == MultiDocDetector.YES) askMulti(files, pdf); else askNewOrAttach(files, pdf); });
            return;
        }
        PickerDialogs.optionSheet(this, "Attach to which case?", "Each file keeps its own category and stage",
                cases, -1, which -> {
                    String matter = cases.get(which);
                    File f = files.get(0);
                    if (multiMode == MultiDocDetector.NO && pdf && files.size() == 1) {
                        // the marker's details card starts with this matter; category (marked pages) and stage are set there
                        getSharedPreferences("pdf_matters", MODE_PRIVATE).edit()
                                .putString(f.getAbsolutePath(), matter).apply();
                        openMarker(f);
                    } else {
                        openNew(files, matter, multiMode);
                    }
                    finish();
                }, () -> { if (multiMode == MultiDocDetector.YES) askMulti(files, pdf); else askNewOrAttach(files, pdf); });
    }

    /** The Document Vault opens in pick mode: a radio on each card, Confirm / Cancel at the bottom instead of the tabs. */
    private void pickDocument(final ArrayList<File> files, final boolean pdf) {
        pendingFiles = files;
        pendingPdf = pdf;
        Intent i = new Intent(this, MarkedFilesActivity.class);
        i.putExtra("pick_doc", true);
        i.putExtra("pick_general_only", true);      // only General Document Vault cards can be chosen
        startActivityForResult(i, REQ_PICK_DOC);
    }

    /** After Confirm: this file's name (+ category for images / Word). The matter is already the card's: it is NOT asked again. */
    private void askDetails(final ArrayList<File> files, final LegalStore.Doc d, final boolean pdf) {
        android.widget.LinearLayout form = LegalUi.form(this);
        final android.widget.EditText nameEt = LegalUi.text(this, form,
                files.size() > 1 ? "Name / caption (numbered 1, 2, 3...)" : "File name / caption",
                DocFileCard.stem(files.get(0).getName()));
        String cur = d.category == null || d.category.equals("Multiple") ? "" : d.category;
        final CategoryPicker cp = new CategoryPicker(this, form, cur);
        final String[] stg = {d.stage == null ? "" : d.stage};
        LegalUi.label(this, form, "Stage");
        final StageStore stages = new StageStore(this);
        final android.widget.TextView[] stv = new android.widget.TextView[1];
        stv[0] = FieldStyle.dropdown(this, form, "Select stage", v ->
                PickerDialogs.stageSheet(this, "Stage", () -> stages.list(), stg[0],
                        s -> { stg[0] = s; FieldStyle.setDropdownValue(stv[0], stg[0], "Select stage"); },
                        s -> stages.add(s),
                        (old, neu) -> {
                            stages.rename(old, neu);
                            if (old.equals(stg[0])) stg[0] = neu;
                            FieldStyle.setDropdownValue(stv[0], stg[0], "Select stage");
                        },
                        gone -> {
                            stages.delete(gone);
                            if (gone.equals(stg[0])) stg[0] = "";
                            FieldStyle.setDropdownValue(stv[0], stg[0], "Select stage");
                        },
                        (i, j) -> stages.swap(i, j)),
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
        FieldStyle.setDropdownValue(stv[0], stg[0], "Select stage");
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(form);
        final android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
                .setTitle("Add to \"" + d.name + "\"")
                .setView(sv)
                .setPositiveButton("OK", null)
                .setNegativeButton("Cancel", (x, w) -> askNewOrAttach(files, pdf))
                .setOnCancelListener(x -> askNewOrAttach(files, pdf))
                .create();
        dlg.show();
        dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!LegalUi.need(this, nameEt, "File name")) return;
            pendingName = LegalUi.str(nameEt);
            pendingCat = cp.get().trim();
            pendingStage = stg[0];
            dlg.dismiss();
            manageThenAttach(files, d, pdf);
        });
    }

    /** Stage is card-level: a stage different from the card's sends the new files to the card with that stage. */
    private void restage(LegalStore ls, LegalStore.Doc d, java.util.List<File> moved) {
        if (pendingStage == null) return;
        String st = pendingStage.trim();
        if (st.equals(d.stage == null ? "" : d.stage.trim())) return;
        String m = d.caseName == null ? "" : d.caseName.trim();
        if (m.isEmpty() || m.equals("[]") || m.equals(MatterPicker.VAULT)) m = "General Document Vault";
        String cat = pendingCat != null && !pendingCat.isEmpty() ? pendingCat : "Other";
        for (File f : moved) {
            if (d.more.isEmpty()) break;          // never strip the card's last file
            DocMove.moveToCard(this, ls, d, PhotoFlow.uri(this, f).toString(), m, st, cat, null);
        }
    }

    private void applyCaption(File moved, int idx, int total) {
        if (pendingName == null || moved == null) return;
        FileCaption.set(this, moved.getAbsolutePath(), total > 1 ? pendingName + " (" + (idx + 1) + ")" : pendingName);
    }

    /** Image / Word share: ONE box with the card's current files + the new ones, so they can be reordered / removed first. */
    private void manageThenAttach(final ArrayList<File> files, final LegalStore.Doc d, final boolean pdf) {
        if (pdf) { attach(files, d, true); return; }
        final java.util.List<Uri> all = new ArrayList<>();
        if (d.uri != null && !d.uri.isEmpty()) all.add(Uri.parse(d.uri));
        for (String x : d.more) all.add(Uri.parse(x));
        final java.util.Map<String, File> incoming = new java.util.HashMap<>();
        for (File f : files) {
            Uri u = PhotoFlow.uri(this, f);
            incoming.put(u.toString(), f);
            all.add(u);
        }
        final boolean[] saved = {false};
        // reference names + category instead of real file names; the new files are marked and can be placed anywhere
        final int existing = all.size() - files.size();
        final java.util.Map<String, File> byUri = DataFolder.documentsByUri(this);
        final MarkStore mst = new MarkStore(this);
        final java.util.List<String> order = new ArrayList<>();
        for (Uri u : all) order.add(u.toString());
        final FileSetDialog.Options opt = new FileSetDialog.Options();
        opt.allowAdd = false;
        opt.hint = (files.size() == 1 ? "The NEW file is" : "The NEW files are") + " at the end. Use Place to put "
                + (files.size() == 1 ? "it" : "one") + " at the start or after any file, or the arrows.";
        opt.fresh.addAll(incoming.keySet());
        opt.label = u -> {
            String us = u.toString();
            File f = incoming.get(us);
            if (f != null) {
                int n = 0;
                for (File x : files) { if (x == f) break; n++; }
                return pendingName == null ? DocFileCard.stem(f.getName())
                        : (files.size() > 1 ? pendingName + " (" + (n + 1) + ")" : pendingName);
            }
            File ex = byUri.get(us);
            String cap = ex == null ? "" : FileCaption.get(this, ex.getAbsolutePath());
            return cap.isEmpty() ? DocFileCard.stem(d.name) + " (" + (order.indexOf(us) + 1) + ")" : cap;
        };
        opt.sub = u -> {
            String us = u.toString();
            if (incoming.containsKey(us)) return pendingCat == null || pendingCat.isEmpty() ? "" : pendingCat;
            File ex = byUri.get(us);
            if (ex == null) return "";
            java.util.Set<String> cs = new java.util.TreeSet<>(mst.all(ex.getAbsolutePath()).values());
            return cs.isEmpty() ? "" : (cs.size() == 1 ? cs.iterator().next() : "Multiple");
        };
        FileSetDialog.show(this, flow, "Files in \"" + d.name + "\"", all, list -> {
            saved[0] = true;
            attachOrdered(list, incoming, d);
        }, () -> { if (!saved[0]) askNewOrAttach(files, pdf); }, opt);
    }

    /** Saves the card with the files in the order chosen in the box; new files move into the Documents folder. */
    private void attachOrdered(java.util.List<Uri> list, java.util.Map<String, File> incoming, LegalStore.Doc d) {
        LegalStore ls = new LegalStore(this);
        MarkStore ms = new MarkStore(this);
        String m = d.caseName == null ? "" : d.caseName.trim();
        boolean general = m.isEmpty() || m.equals("[]") || m.equals("General Document Vault");
        java.util.List<Uri> finalList = new ArrayList<>();
        int added = 0;
        int ii = 0;
        java.util.List<File> movedAll = new ArrayList<>();
        for (Uri u : list) {
            File f = incoming.get(u.toString());
            if (f == null) { finalList.add(u); continue; }
            File moved = DataFolder.moveIntoDocuments(this, f);
            if (moved == null) continue;
            finalList.add(PhotoFlow.uri(this, moved));
            String path = moved.getAbsolutePath();
            applyCaption(moved, ii++, incoming.size());
            movedAll.add(moved);
            if (pendingCat != null && !pendingCat.isEmpty()) {
                ms.set(path, 0, pendingCat);
            } else if (general && ms.all(path).isEmpty() && d.category != null && !d.category.isEmpty()
                    && !d.category.equals("Multiple")) {
                ms.set(path, 0, d.category);
            }
            added++;
        }
        if (finalList.isEmpty()) { finish(); return; }
        d.uri = finalList.get(0).toString();
        d.more.clear();
        for (int k = 1; k < finalList.size(); k++) d.more.add(finalList.get(k).toString());
        d.size = FileSetDialog.totalSize(this, finalList);
        d.fileName = FileSetDialog.label(this, finalList);
        ls.saveDoc(d);
        restage(ls, d, movedAll);
        Toast.makeText(this, added + (added == 1 ? " file" : " files") + " added to \"" + d.name + "\"",
                Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, MarkedFilesActivity.class));      // stay on the Document Vault, tabs enabled
        finish();
    }

    /** The shared files join the chosen document: same card, same name / category / stage / matter. */
    private void attach(ArrayList<File> files, LegalStore.Doc d, boolean pdf) {
        LegalStore ls = new LegalStore(this);
        MarkStore ms = new MarkStore(this);
        String m = d.caseName == null ? "" : d.caseName.trim();
        boolean general = m.isEmpty() || m.equals("[]") || m.equals("General Document Vault");
        int added = 0;
        File firstAdded = null;
        java.util.List<File> movedAll = new ArrayList<>();
        for (File f : files) {
            File moved = DataFolder.moveIntoDocuments(this, f);
            if (moved == null) continue;
            String u = PhotoFlow.uri(this, moved).toString();
            if (u.equals(d.uri) || d.more.contains(u)) continue;      // already part of this document
            d.more.add(u);
            String path = moved.getAbsolutePath();
            if (firstAdded == null) firstAdded = moved;
            applyCaption(moved, files.indexOf(f), files.size());
            movedAll.add(moved);
            if (pdf && pendingCat != null && !pendingCat.isEmpty()) {
                int pc = Math.max(1, DocEdit.pageCount(moved));      // every page starts in the chosen type; refine in the marker
                for (int pg = 0; pg < pc; pg++) ms.set(path, pg, pendingCat);
            } else if (!pdf && pendingCat != null && !pendingCat.isEmpty()) {
                ms.set(path, 0, pendingCat);
            } else if (!pdf && general && ms.all(path).isEmpty() && d.category != null && !d.category.isEmpty()
                    && !d.category.equals("Multiple")) {
                ms.set(path, 0, d.category);          // an image / Word file = one page, marked like the first file
            }
            added++;
        }
        if (added == 0) {
            Toast.makeText(this, "Nothing added (already in this document, or file could not be moved)",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        java.util.List<Uri> group = new ArrayList<>();
        group.add(Uri.parse(d.uri));
        for (String x : d.more) group.add(Uri.parse(x));
        d.size = FileSetDialog.totalSize(this, group);
        d.fileName = FileSetDialog.label(this, group);
        ls.saveDoc(d);            // also removes the originals the files were taken from
        restage(ls, d, movedAll);
        Toast.makeText(this, added + (added == 1 ? " file" : " files") + " added to \"" + d.name + "\"",
                Toast.LENGTH_LONG).show();
        if (pdf) openMarker(firstAdded);          // PDF pages are marked in the Category marker
        else startActivity(new Intent(this, MarkedFilesActivity.class));
        finish();
    }
}
