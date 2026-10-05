package com.example.twobuttons;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
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

        @Override
        public String toString() {
            return name + "\n" + path;
        }
    }

    private List<Pdf> allPdfs = new ArrayList<>();
    private ArrayAdapter<Pdf> adapter;
    private EditText searchBox;
    private TextView status;
    private Button findButton;
    private boolean scanned = false;
    private boolean waitingForPermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

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

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                new ArrayList<Pdf>());
        ListView list = new ListView(this);
        list.setAdapter(adapter);

        root.addView(findButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(searchBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

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
                        if (!name.equals("Android") && !name.startsWith(".")) stack.push(f);
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
        for (Pdf p : allPdfs) {
            if (q.isEmpty() || p.name.toLowerCase(Locale.ROOT).contains(q)) adapter.add(p);
        }
        status.setText(adapter.getCount() + " of " + allPdfs.size() + " PDFs");
    }
}
