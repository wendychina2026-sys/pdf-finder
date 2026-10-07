package com.example.twobuttons;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class PdfActions {

    interface Host {
        List<Pdf> pdfs();        // master list
        void onListChanged();    // re-apply filter after delete
    }

    private final Activity activity;
    private final PdfAdapter adapter;
    private final Host host;
    private LinearLayout bar;
    private TextView countText;
    private Button deleteButton;

    PdfActions(Activity activity, PdfAdapter adapter, Host host) {
        this.activity = activity;
        this.adapter = adapter;
        this.host = host;
    }

    // Top bar shown in selection mode: "N selected | All | Delete | Cancel"
    LinearLayout buildSelectionBar() {
        bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setVisibility(View.GONE);

        countText = new TextView(activity);
        countText.setTextSize(16);
        countText.setTypeface(null, Typeface.BOLD);

        Button all = new Button(activity);
        all.setText("All");
        all.setOnClickListener(v -> {
            for (int i = 0; i < adapter.getCount(); i++) {
                adapter.selected.add(adapter.getItem(i).path);
            }
            refresh();
        });

        deleteButton = new Button(activity);
        deleteButton.setText("Delete");
        deleteButton.setOnClickListener(v -> confirmDelete(new ArrayList<>(adapter.selected)));

        Button cancel = new Button(activity);
        cancel.setText("Cancel");
        cancel.setOnClickListener(v -> exitSelection());

        bar.addView(countText, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(all);
        bar.addView(deleteButton);
        bar.addView(cancel);
        return bar;
    }

    void onItemClick(Pdf p) {
        if (adapter.selectionMode) toggle(p); else open(p);
    }

    void onItemLongClick(Pdf p) {
        if (adapter.selectionMode) toggle(p); else showOptions(p);
    }

    // Back button: leave selection mode. Returns true if it consumed the press.
    boolean handleBack() {
        if (adapter.selectionMode) {
            exitSelection();
            return true;
        }
        return false;
    }

    void refresh() {
        if (countText != null) {
            countText.setText(adapter.selected.size() + " selected");
            deleteButton.setEnabled(!adapter.selected.isEmpty());
        }
        adapter.notifyDataSetChanged();
    }

    private void toggle(Pdf p) {
        if (!adapter.selected.remove(p.path)) adapter.selected.add(p.path);
        refresh();
    }

    private void exitSelection() {
        adapter.selectionMode = false;
        adapter.selected.clear();
        bar.setVisibility(View.GONE);
        refresh();
    }

    private void showOptions(Pdf p) {
        new AlertDialog.Builder(activity)
                .setTitle(p.name)
                .setItems(new String[]{"Mark", "Open containing folder", "Delete", "Select multiple"}, (d, which) -> {
                    if (which == 0) {
                        Intent i = new Intent(activity, MarkActivity.class);
                        i.putExtra("path", p.path);
                        activity.startActivity(i);
                    } else if (which == 1) {
                        openFolder(p);
                    } else if (which == 2) {
                        List<String> one = new ArrayList<>();
                        one.add(p.path);
                        confirmDelete(one);
                    } else {
                        adapter.selectionMode = true;
                        adapter.selected.add(p.path);
                        bar.setVisibility(View.VISIBLE);
                        refresh();
                    }
                })
                .show();
    }

    // Opens the folder holding the file in the Files app (DocumentsUI).
    private void openFolder(Pdf p) {
        File dir = new File(p.path).getParentFile();
        if (dir == null) {
            Toast.makeText(activity, "Folder not found", Toast.LENGTH_SHORT).show();
            return;
        }
        String docId = folderDocId(dir.getAbsolutePath());
        if (docId != null) {
            Uri uri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents", docId);
            try {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(i);
                return;
            } catch (ActivityNotFoundException ignored) {
                // fall through
            }
            try {
                // Fallback: file picker opened at that folder
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/pdf");
                i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri);
                activity.startActivity(i);
                return;
            } catch (ActivityNotFoundException ignored) {
                // fall through
            }
        }
        Toast.makeText(activity, "Cannot open folder: " + dir.getAbsolutePath(),
                Toast.LENGTH_LONG).show();
    }

    // /storage/emulated/0/X/Y -> primary:X/Y ; /storage/ABCD-1234/X -> ABCD-1234:X
    private static String folderDocId(String path) {
        String[] prefixes = {"/storage/emulated/0", "/sdcard", "/storage/self/primary"};
        for (String pre : prefixes) {
            if (path.equals(pre)) return "primary:";
            if (path.startsWith(pre + "/")) return "primary:" + path.substring(pre.length() + 1);
        }
        if (path.startsWith("/storage/")) {
            String rest = path.substring("/storage/".length());
            int slash = rest.indexOf('/');
            if (slash < 0) return rest + ":";
            return rest.substring(0, slash) + ":" + rest.substring(slash + 1);
        }
        return null;
    }

    private void confirmDelete(List<String> paths) {
        if (paths.isEmpty()) return;
        new AlertDialog.Builder(activity)
                .setTitle("Delete " + paths.size() + (paths.size() == 1 ? " file?" : " files?"))
                .setMessage("This permanently deletes from your phone. It cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> deleteFiles(paths))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteFiles(List<String> paths) {
        int ok = 0, fail = 0;
        Set<String> deleted = new HashSet<>();
        for (String path : paths) {
            if (new File(path).delete()) {
                deleted.add(path);
                ok++;
            } else {
                fail++;
            }
        }
        host.pdfs().removeIf(p -> deleted.contains(p.path));
        if (!deleted.isEmpty()) {
            MediaScannerConnection.scanFile(
                    activity, deleted.toArray(new String[0]), null, null);
        }
        exitSelection();
        host.onListChanged();
        Toast.makeText(activity, "Deleted " + ok + (fail > 0 ? ", failed " + fail : ""),
                Toast.LENGTH_LONG).show();
    }

    private void open(Pdf p) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    activity, activity.getPackageName() + ".fileprovider", new File(p.path));
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/pdf");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, "No PDF viewer app installed", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(activity, "Cannot open: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
