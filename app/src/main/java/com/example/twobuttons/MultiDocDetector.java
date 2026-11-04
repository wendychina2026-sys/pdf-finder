package com.example.twobuttons;

import android.app.Activity;
import android.app.AlertDialog;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Global "more than one document / page" detection, shared by every screen that receives files
 * (share receiver, New Case upload box, ...). Detects, asks the user to confirm, and reports the answer;
 * the screen then switches itself to "details per file" mode.
 */
final class MultiDocDetector {
    /** Intent extra handed to the upload screen: ASK (not asked yet), YES (confirmed), NO (declined). */
    static final String EXTRA_MODE = "multi_doc_mode";
    static final int ASK = 0;
    static final int YES = 1;
    static final int NO = 2;

    private MultiDocDetector() { }

    /** More than one file, or one file with more than one page. */
    static boolean detect(List<File> files) {
        if (files == null || files.isEmpty()) return false;
        return files.size() > 1 || pages(files.get(0)) > 1;
    }

    static int totalPages(List<File> files) {
        int n = 0;
        for (File f : files) n += pages(f);
        return n;
    }

    /** PDF: its page count; image / Word file: one page. */
    static int pages(File f) {
        if (f == null || !f.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) return 1;
        try (android.os.ParcelFileDescriptor pf = android.os.ParcelFileDescriptor.open(f,
                android.os.ParcelFileDescriptor.MODE_READ_ONLY);
             android.graphics.pdf.PdfRenderer pr = new android.graphics.pdf.PdfRenderer(pf)) {
            return Math.max(1, pr.getPageCount());
        } catch (Exception e) {
            return 1;
        }
    }

    /** Waits for the user: yes = details for each file, no = the usual single document. */
    static void confirm(Activity a, int files, int pages, Runnable yes, Runnable no) {
        String what = files > 1
                ? files + " files" + (pages > files ? " (" + pages + " pages)" : "")
                : pages + " pages";
        new AlertDialog.Builder(a)
                .setTitle("Multiple documents detected")
                .setMessage(what + " found. Set name, category, stage and matter for each file separately?")
                .setCancelable(false)
                .setPositiveButton("Yes, per file", (d, w) -> yes.run())
                .setNegativeButton("No, one document", (d, w) -> no.run())
                .show();
    }
}
