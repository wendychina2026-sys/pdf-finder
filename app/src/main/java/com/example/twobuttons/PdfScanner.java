package com.example.twobuttons;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Walks the phone's storage for PDFs, images and videos. The app's own PdfFinder data folder is never
 * scanned (its PDFs are already managed in Document Vault). WhatsApp and Telegram folders are included:
 * the old ones (WhatsApp/, Telegram/) and the new ones under Android/media (needs "All files access").
 * Folders whose name starts with a dot are skipped, as before.
 */
class PdfScanner {

    interface Callback {
        void onDone(List<Pdf> found);
    }

    static void scan(Activity activity, Callback callback) {
        new Thread(() -> {
            List<Pdf> found = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            Set<String> skip = skipDirs();
            ArrayDeque<File> stack = new ArrayDeque<>();
            for (File r : storageRoots(activity)) stack.push(r);
            while (!stack.isEmpty()) {
                File[] children = stack.pop().listFiles();
                if (children == null) continue;
                for (File f : children) {
                    String name = f.getName();
                    if (f.isDirectory()) {
                        String p = f.getAbsolutePath();
                        // Android/media allowed. Android/data + obb blocked on 11+.
                        boolean blocked = Build.VERSION.SDK_INT >= 30
                                && (p.endsWith("/Android/data") || p.endsWith("/Android/obb"));
                        if (!blocked && !name.startsWith(".") && !skip.contains(canon(f))) stack.push(f);
                    } else {
                        int kind = Pdf.kindOf(name);
                        if (kind < 0) continue;
                        if (!seen.add(canon(f))) continue;        // same file reached from two roots
                        found.add(new Pdf(name, f.getAbsolutePath(), f.length(), kind));
                    }
                }
            }
            Collections.sort(found, (a, b) -> a.name.compareToIgnoreCase(b.name));
            activity.runOnUiThread(() -> callback.onDone(found));
        }).start();
    }

    /** The PdfFinder data folder(s): not scanned. */
    private static Set<String> skipDirs() {
        Set<String> s = new LinkedHashSet<>();
        s.add(canon(new File(Environment.getExternalStorageDirectory(), DataFolder.ROOT_NAME)));
        File r = DataFolder.root();
        if (r != null) s.add(canon(r));
        return s;
    }

    private static String canon(File f) {
        try {
            return f.getCanonicalPath();
        } catch (IOException e) {
            return f.getAbsolutePath();
        }
    }

    private static List<File> storageRoots(Context c) {
        Set<String> paths = new LinkedHashSet<>();
        paths.add(Environment.getExternalStorageDirectory().getAbsolutePath());
        File[] dirs = c.getExternalFilesDirs(null);
        if (dirs != null) {
            for (File d : dirs) {
                if (d == null) continue;
                String p = d.getAbsolutePath();
                int i = p.indexOf("/Android/data");
                if (i > 0) paths.add(p.substring(0, i)); // SD card root
            }
        }
        List<File> roots = new ArrayList<>();
        for (String p : paths) roots.add(new File(p));
        return roots;
    }
}
