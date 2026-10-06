package com.example.twobuttons;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class PdfScanner {

    interface Callback {
        void onDone(List<Pdf> found);
    }

    static void scan(Activity activity, Callback callback) {
        new Thread(() -> {
            List<Pdf> found = new ArrayList<>();
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
                        if (!blocked && !name.startsWith(".")) stack.push(f);
                    } else if (name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                        found.add(new Pdf(name, f.getAbsolutePath()));
                    }
                }
            }
            Collections.sort(found, (a, b) -> a.name.compareToIgnoreCase(b.name));
            activity.runOnUiThread(() -> callback.onDone(found));
        }).start();
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
