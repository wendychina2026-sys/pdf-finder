package com.example.twobuttons;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Scans phone for PDFs, images and videos and lists them. Search by file name lives here. */
public class FindPdfsActivity extends BaseActivity {

    @Override
    protected int navIndex() { return 1; }

    private List<Pdf> allPdfs = new ArrayList<>();
    private PdfAdapter adapter;
    private PdfActions actions;
    private PermissionHelper perms;
    private EditText searchBox;
    private TextView status;
    private Button rescanButton;
    private LinearLayout chips;
    private static final String[] KINDS = {"All", "PDF", "Images", "Videos"};
    private int kindSel = 0;
    private static final String[] SORTS = {"Name A-Z", "Size: small to large", "Size: large to small",
            "Date: newest first", "Date: oldest first"};
    private TextView sortChip, dateChip;
    private static final String[] DATES = {"All dates", "Today", "Last 7 days", "Last 30 days", "This year",
            "Pick date range..."};
    private int dateSel = 0;
    private String dateText = "All dates";
    private boolean scanned = false;
    private boolean waitingForPermission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Find files");

        perms = new PermissionHelper(this);
        adapter = new PdfAdapter(this);
        actions = new PdfActions(this, adapter, new PdfActions.Host() {
            @Override public List<Pdf> pdfs() { return allPdfs; }
            @Override public void onListChanged() { applyFilter(); }
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        rescanButton = new Button(this);
        rescanButton.setText("Rescan");
        rescanButton.setOnClickListener(v -> startScan());

        searchBox = new RoundEdit(this);
        FieldStyle.hint(searchBox, "Search file name");
        searchBox.setSingleLine(true);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (scanned) applyFilter();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, 8, 0, 8);
        renderChips();

        status = new TextView(this);
        status.setText("Tap Rescan to search for PDFs, images and videos");
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
        root.addView(actions.buildSelectionBar(), wide);
        root.addView(searchBox, wide);
        sortChip = new TextView(this);
        sortChip.setTextSize(14);
        sortChip.setTextColor(AppTheme.accent(this));
        sortChip.setPadding(CaseViews.dp(this, 14), CaseViews.dp(this, 8), CaseViews.dp(this, 14), CaseViews.dp(this, 8));
        sortChip.setBackground(CaseViews.box(this, AppTheme.field(this), 10, AppTheme.fieldStroke(this), 1));
        sortChip.setOnClickListener(v -> PickerDialogs.optionSheet(this, "Sort by", null,
                java.util.Arrays.asList(SORTS), adapter.sortMode, which -> {
                    adapter.sortMode = which;
                    updateSortChip();
                    if (scanned) applyFilter();
                }));
        updateSortChip();
        dateChip = new TextView(this);
        dateChip.setTextSize(14);
        dateChip.setTextColor(AppTheme.accent(this));
        dateChip.setPadding(CaseViews.dp(this, 14), CaseViews.dp(this, 8), CaseViews.dp(this, 14), CaseViews.dp(this, 8));
        dateChip.setBackground(CaseViews.box(this, AppTheme.field(this), 10, AppTheme.fieldStroke(this), 1));
        dateChip.setOnClickListener(v -> PickerDialogs.optionSheet(this, "Filter by date", "File last modified",
                java.util.Arrays.asList(DATES), dateSel, this::pickDate));
        updateDateChip();
        LinearLayout sortRow = new LinearLayout(this);
        sortRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chipLp.rightMargin = CaseViews.dp(this, 8);
        sortRow.addView(sortChip, chipLp);
        sortRow.addView(dateChip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(chips, wide);
        root.addView(sortRow, wide);
        root.addView(rescanButton, wide);
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
            status.setText("Permission denied - cannot search for files");
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
        rescanButton.setEnabled(false);
        PdfScanner.scan(this, found -> {
            allPdfs = found;
            scanned = true;
            rescanButton.setEnabled(true);
            applyFilter();
        });
    }

    private void renderChips() {
        chips.removeAllViews();
        for (int i = 0; i < KINDS.length; i++) {
            final int idx = i;
            boolean on = i == kindSel;
            TextView t = new TextView(this);
            t.setText((on ? "\u2713 " : "") + KINDS[i]);
            t.setTextSize(14);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(on ? AppTheme.accent(this) : AppTheme.text(this));
            t.setPadding(CaseViews.dp(this, 14), CaseViews.dp(this, 8), CaseViews.dp(this, 14), CaseViews.dp(this, 8));
            int fill = on ? (AppTheme.isDark(this) ? 0xFF2A2C44 : 0xFFBFE3FA) : AppTheme.field(this);
            t.setBackground(CaseViews.box(this, fill, 10, AppTheme.fieldStroke(this), on ? 0 : 1));
            t.setOnClickListener(v -> {
                kindSel = idx;
                adapter.kindFilter = idx == 0 ? -1 : idx - 1;   // PDF=0, Images=1, Videos=2
                renderChips();
                if (scanned) applyFilter();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = CaseViews.dp(this, 8);
            chips.addView(t, lp);
        }
    }

    private static long startOfDay(java.util.Calendar c) {
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private void pickDate(int which) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        long today = startOfDay(c);
        long day = 24L * 60 * 60 * 1000;
        switch (which) {
            case 0: setDate(which, "All dates", 0, 0); break;
            case 1: setDate(which, "Today", today, 0); break;
            case 2: setDate(which, "Last 7 days", today - 6 * day, 0); break;
            case 3: setDate(which, "Last 30 days", today - 29 * day, 0); break;
            case 4: {
                java.util.Calendar y = java.util.Calendar.getInstance();
                y.set(java.util.Calendar.DAY_OF_YEAR, 1);
                setDate(which, "This year", startOfDay(y), 0);
                break;
            }
            default: pickRange(); break;
        }
    }

    /** From date, then To date (both inclusive). */
    private void pickRange() {
        final java.util.Calendar now = java.util.Calendar.getInstance();
        new android.app.DatePickerDialog(this, (v1, y1, m1, d1) -> {
            java.util.Calendar f = java.util.Calendar.getInstance();
            f.set(y1, m1, d1);
            final long from = startOfDay(f);
            new android.app.DatePickerDialog(this, (v2, y2, m2, d2) -> {
                java.util.Calendar t = java.util.Calendar.getInstance();
                t.set(y2, m2, d2);
                long to = startOfDay(t) + 24L * 60 * 60 * 1000 - 1;
                if (to < from) {
                    android.widget.Toast.makeText(this, "To date is before From date", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US);
                setDate(5, df.format(new java.util.Date(from)) + " - " + df.format(new java.util.Date(to)), from, to);
            }, y1, m1, d1).show();
            android.widget.Toast.makeText(this, "Pick the To date", android.widget.Toast.LENGTH_SHORT).show();
        }, now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH),
                now.get(java.util.Calendar.DAY_OF_MONTH)).show();
        android.widget.Toast.makeText(this, "Pick the From date", android.widget.Toast.LENGTH_SHORT).show();
    }

    private void setDate(int sel, String text, long from, long to) {
        dateSel = sel;
        dateText = text;
        adapter.dateFrom = from;
        adapter.dateTo = to;
        updateDateChip();
        if (scanned) applyFilter();
    }

    private void updateDateChip() {
        dateChip.setText("Date: " + dateText + " \u25BE");
    }

    private void updateSortChip() {
        sortChip.setText("Sort: " + SORTS[adapter.sortMode] + " \u25BE");
    }

    private void applyFilter() {
        int shown = adapter.filter(allPdfs, searchBox.getText().toString());
        status.setText(shown + " of " + allPdfs.size() + " files");
        actions.refresh();
    }
}
