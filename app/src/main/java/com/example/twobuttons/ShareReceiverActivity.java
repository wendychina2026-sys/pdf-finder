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
            if (chosen != null) manageThenAttach(files, chosen, pendingPdf);
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

        if (first == null && firstWord == null) {
            Toast.makeText(this, "Cannot read the shared file", Toast.LENGTH_LONG).show();
        } else if (first != null) {
            if (copied > 1) {
                Toast.makeText(this, copied + " PDFs saved. Opening the first.",
                        Toast.LENGTH_LONG).show();
            }
            if (wordCount > 0) {
                Toast.makeText(this, wordCount + " file(s) saved in the Documents folder.",
                        Toast.LENGTH_LONG).show();
            }
            // new document (page marker) or attach to a document already in the Vault
            if (!attachableDocs(true).isEmpty()) {
                askNewOrAttach(pdfs, true);
                return;                 // finishes after the choice
            }
            openMarker(first);
        } else {
            if (wordCount > 1) {
                Toast.makeText(this, wordCount + " files saved. All of them open in one upload box.",
                        Toast.LENGTH_LONG).show();
            }
            // a Word file or image has no pages to mark: new document, or attach to a document already in the Vault
            if (!attachableDocs(false).isEmpty()) {
                askNewOrAttach(words, false);
                return;                 // finishes after the choice
            }
            openNew(words);
        }
        finish();
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
        Intent i = new Intent(this, NewCaseActivity.class);
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

    private void askNewOrAttach(final ArrayList<File> files, final boolean pdf) {
        java.util.List<String> items = new ArrayList<>();
        items.add("New document (its own separate card)");
        items.add("New document under an already saved case");
        items.add("Add to a saved document (joins that card)");
        PickerDialogs.optionSheet(this, "Shared file",
                files.size() + (files.size() == 1 ? " file" : " files"), items, -1, which -> {
                    if (which == 0) {
                        if (pdf) openMarker(files.get(0)); else openNew(files);
                        finish();
                    } else if (which == 1) pickCase(files, pdf);
                    else pickDocument(files, pdf);
                }, this::finish);
    }

    /** Own card with its OWN category and stage, but under the same matter as a document already saved. */
    private void pickCase(final ArrayList<File> files, final boolean pdf) {
        final java.util.List<String> matters = new ArrayList<>();
        for (LegalStore.Doc d : attachableDocs(true)) {
            String m = d.caseName == null ? "" : d.caseName.trim();
            if (m.isEmpty() || m.equals("[]") || m.equals("General Document Vault")) m = MatterPicker.VAULT;
            if (!matters.contains(m)) matters.add(m);
        }
        java.util.List<String> shown = new ArrayList<>();
        for (String m : matters) shown.add(m.equals(MatterPicker.VAULT) ? "General Document Vault" : m);
        PickerDialogs.optionSheet(this, "Same case as which?", "The new file keeps its own category and stage",
                shown, -1, which -> {
                    String matter = matters.get(which);
                    File f = files.get(0);
                    if (pdf) {
                        // the marker's details card starts with this matter; category (marked pages) and stage are set there
                        getSharedPreferences("pdf_matters", MODE_PRIVATE).edit()
                                .putString(f.getAbsolutePath(), matter).apply();
                        openMarker(f);
                    } else {
                        openNew(files, matter);
                    }
                    finish();
                }, () -> askNewOrAttach(files, pdf));
    }

    /** The Document Vault opens in pick mode: a radio on each card, Confirm / Cancel at the bottom instead of the tabs. */
    private void pickDocument(final ArrayList<File> files, final boolean pdf) {
        pendingFiles = files;
        pendingPdf = pdf;
        Intent i = new Intent(this, MarkedFilesActivity.class);
        i.putExtra("pick_doc", true);
        startActivityForResult(i, REQ_PICK_DOC);
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
        FileSetDialog.show(this, flow, "Files in \"" + d.name + "\"", all, list -> {
            saved[0] = true;
            attachOrdered(list, incoming, d);
        }, () -> { if (!saved[0]) askNewOrAttach(files, pdf); });
    }

    /** Saves the card with the files in the order chosen in the box; new files move into the Documents folder. */
    private void attachOrdered(java.util.List<Uri> list, java.util.Map<String, File> incoming, LegalStore.Doc d) {
        LegalStore ls = new LegalStore(this);
        MarkStore ms = new MarkStore(this);
        String m = d.caseName == null ? "" : d.caseName.trim();
        boolean general = m.isEmpty() || m.equals("[]") || m.equals("General Document Vault");
        java.util.List<Uri> finalList = new ArrayList<>();
        int added = 0;
        for (Uri u : list) {
            File f = incoming.get(u.toString());
            if (f == null) { finalList.add(u); continue; }
            File moved = DataFolder.moveIntoDocuments(this, f);
            if (moved == null) continue;
            finalList.add(PhotoFlow.uri(this, moved));
            String path = moved.getAbsolutePath();
            if (general && ms.all(path).isEmpty() && d.category != null && !d.category.isEmpty()
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
        for (File f : files) {
            File moved = DataFolder.moveIntoDocuments(this, f);
            if (moved == null) continue;
            String u = PhotoFlow.uri(this, moved).toString();
            if (u.equals(d.uri) || d.more.contains(u)) continue;      // already part of this document
            d.more.add(u);
            String path = moved.getAbsolutePath();
            if (firstAdded == null) firstAdded = moved;
            if (!pdf && general && ms.all(path).isEmpty() && d.category != null && !d.category.isEmpty()
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
        Toast.makeText(this, added + (added == 1 ? " file" : " files") + " added to \"" + d.name + "\"",
                Toast.LENGTH_LONG).show();
        if (pdf) openMarker(firstAdded);          // PDF pages are marked in the Category marker
        else startActivity(new Intent(this, MarkedFilesActivity.class));
        finish();
    }
}
