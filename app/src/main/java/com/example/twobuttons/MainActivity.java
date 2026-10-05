package com.example.twobuttons;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    static class Pdf {
        final String name;
        final String path;

        Pdf(String name, String path) {
            this.name = name;
            this.path = path;
        }
    }

    // One list row: [checkbox] file name (bold) / path (small, grey)
    static class Row extends LinearLayout {
        final CheckBox box;
        final TextView name;
        final TextView path;

        Row(Context c) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(16, 20, 16, 20);

            box = new CheckBox(c);
            box.setFocusable(false);
            box.setClickable(false);

            LinearLayout col = new LinearLayout(c);
            col.setOrientation(VERTICAL);
            name = new TextView(c);
            name.setTextSize(16);
            name.setTypeface(null, Typeface.BOLD);
            name.setTextColor(Color.BLACK);
            path = new TextView(c);
            path.setTextSize(12);
            path.setTextColor(Color.GRAY);
            col.addView(name);
            col.addView(path);

            addView(box, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            addView(col, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    private class PdfAdapter extends ArrayAdapter<Pdf> {
        PdfAdapter() {
            super(MainActivity.this, 0, new ArrayList<Pdf>());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row r = (convertView == null) ? new Row(getContext()) : (Row) convertView;
            Pdf p = getItem(position);
            r.name.setText(p.name);
            r.path.setText(p.path);
            boolean isSelected = selected.contains(p.path);
            r.box.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
            r.box.setChecked(isSelected);
            r.setBackgroundColor(isSelected ? 0x2233AAFF : Color.TRANSPARENT);
            return r;
        }
    }

    private List<Pdf> allPdfs = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private boolean selectionMode = false;
    private PdfAdapter adapter;
    private EditText searchBox;
    private TextView status;
    private Button findButton;
    private LinearLayout selectionBar;
    private TextView selectedCount;
    private Button deleteButton;
    private boolean scanned = false;
    private boolean waitingForPermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        // Selection bar (top, hidden until selection mode)
        selectionBar = new LinearLayout(this);
        selectionBar.setOrientation(LinearLayout.HORIZONTAL);
        selectionBar.setGravity(Gravity.CENTER_VERTICAL);
        selectionBar.setVisibility(View.GONE);
        selectedCount = new TextView(this);
        selectedCount.setTextSize(16);
        selectedCount.setTypeface(null, Typeface.BOLD);
        Button allButton = new Button(this);
        allButton.setText("All");
        allButton.setOnClickListener(v -> {
            for (int i = 0; i < adapter.getCount(); i++) selected.add(adapter.getItem(i).path);
            refreshSelection();
        });
        deleteButton = new Button(this);
        deleteButton.setText("Delete");
        deleteButton.setOnClickListener(v -> confirmDelete(new ArrayList<>(selected)));
        Button cancelButton = new Button(this);
        cancelButton.setText("Cancel");
        cancelButton.setOnClickListener(v -> exitSelection());
        selectionBar.addView(selectedCount, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        selectionBar.addView(allButton);
        selectionBar.addView(deleteButton);
        selectionBar.addView(cancelButton);

        findButton = new Button(this);
        findButton.setText("Find PDFs");
        findButton.setOnClickListener(v -> startScan());

        searchBox = new EditText(this);
        searchBox.setHint("Search PDF name");
        searchBox.setSingleLine(true);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (scanned) applyFilter();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        status = new TextView(this);
        status.setText("Tap Find PDFs to start");
        status.setPadding(0, 16, 0, 16);

        adapter = new PdfAdapter();
        ListView list = new ListView(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) -> {
            Pdf p = adapter.getItem(pos);
            if (selectionMode) toggle(p); else openPdf(p);
        });
        list.setOnItemLongClickListener((parent, view, pos, id) -> {
            Pdf p = adapter.getItem(pos);
            if (selectionMode) toggle(p); else showOptions(p);
            return true;
        });

        LinearLayout.LayoutParams wide = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        root.addView(selectionBar, wide);
        root.addView(findButton, wide);
        root.addView(searchBox, wide);
        root.addView(status, wide);
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    // ---------- open / select / delete ----------

    private void openPdf(Pdf p) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", new File(p.path));
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/pdf");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No PDF viewer app installed", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showOptions(Pdf p) {
        new AlertDialog.Builder(this)
                .setTitle(p.name)
                .setItems(new String[]{"Delete", "Select multiple"}, (d, which) -> {
                    if (which == 0) {
                        List<String> one = new ArrayList<>();
                        one.add(p.path);
                        confirmDelete(one);
                    } else {
                        selectionMode = true;
                        selected.add(p.path);
                        selectionBar.setVisibility(View.VISIBLE);
                        refreshSelection();
                    }
                })
                .show();
    }

    private void toggle(Pdf p) {
        if (!selected.remove(p.path)) selected.add(p.path);
        refreshSelection();
    }

    private void refreshSelection() {
        selectedCount.setText(selected.size() + " selected");
        deleteButton.setEnabled(!selected.isEmpty());
        adapter.notifyDataSetChanged();
    }

    private void exitSelection() {
        selectionMode = false;
        selected.clear();
        selectionBar.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onBackPressed() {
        if (selectionMode) exitSelection(); else super.onBackPressed();
    }

    private void confirmDelete(List<String> paths) {
        if (paths.isEmpty()) return;
        new AlertDialog.Builder(this)
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
        allPdfs.removeIf(p -> deleted.contains(p.path));
        if (!deleted.isEmpty()) {
            MediaScannerConnection.scanFile(this, deleted.toArray(new String[0]), null, null);
        }
        exitSelection();
        applyFilter();
        Toast.makeText(this, "Deleted " + ok + (fail > 0 ? ", failed " + fail : ""),
                Toast.LENGTH_LONG).show();
    }

    // ---------- permissions ----------

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForPermission && hasAccess()) {
            waitingForPermission = false;
            scan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            scan();
        } else {
            status.setText("Permission denied - cannot search for PDFs");
        }
    }

    private boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            return Environment.isExternalStorageManager();
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startScan() {
        if (hasAccess()) {
            scan();
            return;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            waitingForPermission = true;
            Toast.makeText(this, "Turn on \"Allow access to all files\", then go back",
                    Toast.LENGTH_LONG).show();
            Uri uri = Uri.parse("package:" + getPackageName());
            try {
                startActivity(new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
    }

    // ---------- scanning ----------

    private List<File> storageRoots() {
        Set<String> paths = new LinkedHashSet<>();
        paths.add(Environment.getExternalStorageDirectory().getAbsolutePath());
        File[] dirs = getExternalFilesDirs(null);
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

    private void scan() {
        status.setText("Scanning...");
        findButton.setEnabled(false);
        new Thread(() -> {
            List<Pdf> found = new ArrayList<>();
            ArrayDeque<File> stack = new ArrayDeque<>();
            for (File r : storageRoots()) stack.push(r);
            while (!stack.isEmpty()) {
                File[] children = stack.pop().listFiles();
                if (children == null) continue;
                for (File f : children) {
                    String name = f.getName();
                    if (f.isDirectory()) {
                        String p = f.getAbsolutePath();
                        boolean blocked = Build.VERSION.SDK_INT >= 30
                                && (p.endsWith("/Android/data") || p.endsWith("/Android/obb"));
                        if (!blocked && !name.startsWith(".")) stack.push(f);
                    } else if (name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                        found.add(new Pdf(name, f.getAbsolutePath()));
                    }
                }
            }
            Collections.sort(found, (a, b) -> a.name.compareToIgnoreCase(b.name));
            runOnUiThread(() -> {
                allPdfs = found;
                scanned = true;
                findButton.setEnabled(true);
                applyFilter();
            });
        }).start();
    }

    private void applyFilter() {
        String q = searchBox.getText().toString().trim().toLowerCase(Locale.ROOT);
        adapter.clear();
        Set<String> visible = new HashSet<>();
        for (Pdf p : allPdfs) {
            if (q.isEmpty() || p.name.toLowerCase(Locale.ROOT).contains(q)) {
                adapter.add(p);
                visible.add(p.path);
            }
        }
        selected.retainAll(visible); // never keep selected files that are hidden by the filter
        if (selectionMode) refreshSelection();
        status.setText(adapter.getCount() + " of " + allPdfs.size() + " PDFs");
    }
}
