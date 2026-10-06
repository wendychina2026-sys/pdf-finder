package com.example.twobuttons;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private List<Pdf> allPdfs = new ArrayList<>();
    private PdfAdapter adapter;
    private PdfActions actions;
    private PermissionHelper perms;
    private EditText searchBox;
    private TextView status;
    private Button findButton;
    private boolean scanned = false;
    private boolean waitingForPermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        perms = new PermissionHelper(this);
        adapter = new PdfAdapter(this);
        actions = new PdfActions(this, adapter, new PdfActions.Host() {
            @Override public List<Pdf> pdfs() { return allPdfs; }
            @Override public void onListChanged() { applyFilter(); }
        });

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

        ListView list = new ListView(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) ->
                actions.onItemClick(adapter.getItem(pos)));
        list.setOnItemLongClickListener((parent, view, pos, id) -> {
            actions.onItemLongClick(adapter.getItem(pos));
            return true;
        });

        LinearLayout.LayoutParams wide = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("EEE, dd MMM yyyy  hh:mm:ss a");
        clock.setFormat24Hour("EEE, dd MMM yyyy  HH:mm:ss");
        clock.setTextSize(18);
        clock.setGravity(android.view.Gravity.CENTER);
        clock.setPadding(0, 0, 0, 16);
        root.addView(clock, wide);

        root.addView(actions.buildSelectionBar(), wide);
        root.addView(findButton, wide);

        Button markedButton = new Button(this);
        markedButton.setText("Marked pages");
        markedButton.setOnClickListener(v ->
                startActivity(new Intent(this, MarkedActivity.class)));
        root.addView(markedButton, wide);

        Button filesButton = new Button(this);
        filesButton.setText("Marked PDFs");
        filesButton.setOnClickListener(v ->
                startActivity(new Intent(this, MarkedFilesActivity.class)));
        root.addView(filesButton, wide);

        Button searchCatButton = new Button(this);
        searchCatButton.setText("Search by category");
        searchCatButton.setOnClickListener(v ->
                startActivity(new Intent(this, CategorySearchActivity.class)));
        root.addView(searchCatButton, wide);
        root.addView(searchBox, wide);
        root.addView(status, wide);
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForPermission && perms.hasAccess()) {
            waitingForPermission = false;
            scan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] results) {
        super.onRequestPermissionsResult(code, p, results);
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            scan();
        } else {
            status.setText("Permission denied - cannot search for PDFs");
        }
    }

    @Override
    public void onBackPressed() {
        if (!actions.handleBack()) super.onBackPressed();
    }

    private void startScan() {
        if (perms.hasAccess()) {
            scan();
        } else {
            waitingForPermission = Build.VERSION.SDK_INT >= 30;
            perms.request();
        }
    }

    private void scan() {
        status.setText("Scanning...");
        findButton.setEnabled(false);
        PdfScanner.scan(this, found -> {
            allPdfs = found;
            scanned = true;
            findButton.setEnabled(true);
            applyFilter();
        });
    }

    private void applyFilter() {
        int shown = adapter.filter(allPdfs, searchBox.getText().toString());
        status.setText(shown + " of " + allPdfs.size() + " PDFs");
        actions.refresh();
    }
}
