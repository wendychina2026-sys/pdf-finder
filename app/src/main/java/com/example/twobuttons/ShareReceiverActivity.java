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
                Toast.makeText(this, wordCount + " files saved. Opening the first; the rest are in the Documents folder.",
                        Toast.LENGTH_LONG).show();
            }
            // a Word file or image has no pages to mark: new document, or attach to a document already in the Vault
            if (!attachableDocs(false).isEmpty()) {
                askNewOrAttach(words, false);
                return;                 // finishes after the choice
            }
            openNew(firstWord);
        }
        finish();
    }

    /** Category marker: the shared PDF becomes a NEW document, pages marked there. */
    private void openMarker(File f) {
        Intent i = new Intent(this, MarkActivity.class);
        i.putExtra("path", f.getAbsolutePath());
        startActivity(i);
    }

    /** Upload Legal Document popup: the shared file becomes a NEW document. */
    private void openNew(File f) {
        Intent i = new Intent(this, NewCaseActivity.class);
        i.putExtra(NewCaseActivity.EXTRA_UPLOAD_PDF, f.getAbsolutePath());
        startActivity(i);
    }

    /** Saved documents the shared files can join. An image / Word file skips PDF documents (those are marked page by page). */
    private java.util.List<LegalStore.Doc> attachableDocs(boolean pdfShare) {
        java.util.List<LegalStore.Doc> out = new ArrayList<>();
        for (LegalStore.Doc d : new LegalStore(this).docs()) {
            if (d.uri == null || d.uri.isEmpty()) continue;
            if (!pdfShare && DocFileCard.kind(d.name) == 0) continue;
            out.add(d);
        }
        return out;
    }

    private void askNewOrAttach(final ArrayList<File> files, final boolean pdf) {
        java.util.List<String> items = new ArrayList<>();
        items.add("New document");
        items.add("Add to an existing document");
        PickerDialogs.optionSheet(this, "Shared file",
                files.size() + (files.size() == 1 ? " file" : " files"), items, -1, which -> {
                    if (which == 0) {
                        if (pdf) openMarker(files.get(0)); else openNew(files.get(0));
                        finish();
                    } else pickDocument(files, pdf);
                }, this::finish);
    }

    private void pickDocument(final ArrayList<File> files, final boolean pdf) {
        final java.util.List<LegalStore.Doc> docs = attachableDocs(pdf);
        java.util.List<String> items = new ArrayList<>();
        for (LegalStore.Doc d : docs) {
            String m = d.caseName == null ? "" : d.caseName.trim();
            if (m.isEmpty() || m.equals("[]")) m = "General Document Vault";
            items.add(d.name + "  \u2022  " + m + "  \u2022  " + d.category
                    + (d.more.isEmpty() ? "" : "  \u2022  " + (1 + d.more.size()) + " files"));
        }
        PickerDialogs.optionSheet(this, "Add to which document?", null, items, -1,
                which -> attach(files, docs.get(which), pdf), () -> askNewOrAttach(files, pdf));
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
        else startActivity(new Intent(this, DocumentsActivity.class));
        finish();
    }
}
