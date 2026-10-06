package com.example.twobuttons;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Environment;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class PageExtractor {

    // Copy pages (0-based) of srcPath into a new PDF under Documents/PDFFinder/<category>/.
    // Run off the main thread.
    static File extract(Context c, String srcPath, List<Integer> pages, String category)
            throws IOException {
        PDFBoxResourceLoader.init(c.getApplicationContext());

        File dir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "PDFFinder/" + safe(category));
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Cannot create folder " + dir.getAbsolutePath());
        }

        String base = new File(srcPath).getName().replaceAll("(?i)\\.pdf$", "");
        File out = uniqueFile(dir, base + "_" + safe(category), ".pdf");

        List<Integer> sorted = new ArrayList<>(pages);
        Collections.sort(sorted);

        try (PDDocument src = PDDocument.load(new File(srcPath),
                MemoryUsageSetting.setupTempFileOnly());
             PDDocument dst = new PDDocument()) {
            int count = src.getNumberOfPages();
            for (int p : sorted) {
                if (p < 0 || p >= count) continue;
                PDPage original = src.getPage(p);
                PDPage copy = dst.importPage(original);
                // importPage drops inherited attributes: copy them by hand
                copy.setResources(original.getResources());
                copy.setMediaBox(original.getMediaBox());
                copy.setRotation(original.getRotation());
            }
            if (dst.getNumberOfPages() == 0) throw new IOException("No valid pages");
            dst.save(out);
        }

        MediaScannerConnection.scanFile(c, new String[]{out.getAbsolutePath()}, null, null);
        return out;
    }

    private static String safe(String s) {
        return s.replaceAll("[^A-Za-z0-9 _-]", "_");
    }

    private static File uniqueFile(File dir, String base, String ext) {
        File f = new File(dir, base + ext);
        int n = 2;
        while (f.exists()) {
            f = new File(dir, base + " (" + n + ")" + ext);
            n++;
        }
        return f;
    }
}
