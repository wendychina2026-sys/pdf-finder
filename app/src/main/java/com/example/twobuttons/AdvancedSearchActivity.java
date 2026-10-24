package com.example.twobuttons;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import android.widget.HorizontalScrollView;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class AdvancedSearchActivity extends BaseActivity {

    @Override
    protected int navIndex() { return 3; }

    private static final String ALL = "All";

    static class Item {
        final String path;
        final String topics;
        final String details;
        final String topic;   // selected topic filter, "" = all
        String caseName = "";
        int year = 0;
        int marked = 0;       // number of marked pages
        long modified = 0;

        Item(String path, String topics, String details, String topic) {
            this.path = path;
            this.topics = topics;
            this.details = details;
            this.topic = topic;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private BaseAdapter adapter;
    private CaseOptions opts;
    private EditText fileEdit;
    private SmartSearchBar smartSearch;
    private TextView status;
    private TextView sortBtn;
    private View filterDot;
    private MarkStore marks;
    private DetailsStore details;

    // applied filters ("" / 0 = any); the popup edits copies and only writes these on Apply
    private String fCase = "", fSub = "", fSubSub = "", fCourt = "", fLawyer = "", fTopic = "";
    private int fYear = 0;

    private static final String[] SORTS = {
            "File name (A to Z)", "File name (Z to A)", "Case name (A to Z)",
            "Filing year (newest first)", "Filing year (oldest first)",
            "Most marked pages", "Recently modified"};
    private int sortIdx = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().hide();   // no title bar on this page
        marks = new MarkStore(this);
        details = new DetailsStore(this);
        opts = new CaseOptions(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);

        smartSearch = new SmartSearchBar(this, "Search file name or case name");
        fileEdit = smartSearch.edit();
        fileEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { refresh(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        status = new TextView(this);
        status.setTextColor(AppTheme.sub(this));
        status.setTextSize(13);

        sortBtn = new TextView(this);
        sortBtn.setTextSize(13);
        sortBtn.setTypeface(null, Typeface.BOLD);
        sortBtn.setTextColor(AppTheme.accent(this));
        sortBtn.setPadding(CaseViews.dp(this, 10), CaseViews.dp(this, 8), CaseViews.dp(this, 4), CaseViews.dp(this, 8));
        sortBtn.setOnClickListener(v -> chooseSort());
        updateSortLabel();

        adapter = new ItemAdapter();
        ListView list = new ListView(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) -> {
            Intent i = new Intent(this, MarkActivity.class);
            i.putExtra("path", items.get(pos).path);
            i.putExtra("only_sub", items.get(pos).topic);
            startActivity(i);
        });

        LinearLayout.LayoutParams wide = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        // top row: search bar, with the filter button outside it on the right
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        top.addView(smartSearch.view(), new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(buildFilterButton());
        root.addView(top, wide);

        // result count (left) and sort (right)
        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(android.view.Gravity.CENTER_VERTICAL);
        meta.setPadding(0, CaseViews.dp(this, 6), 0, 0);
        meta.addView(status, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        meta.addView(sortBtn);
        root.addView(meta, wide);

        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    // ---------- filter button (outside the search box, on its right) ----------

    private View buildFilterButton() {
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setBackground(CaseViews.box(this, AppTheme.field(this), 10, AppTheme.fieldStroke(this), 1));
        ImageView ic = CaseViews.icon(this, R.drawable.ic_filter, AppTheme.accent(this), 22);
        box.addView(ic, new android.widget.FrameLayout.LayoutParams(
                CaseViews.dp(this, 22), CaseViews.dp(this, 22), android.view.Gravity.CENTER));
        filterDot = new View(this);
        android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dot.setColor(0xFFE53935);
        filterDot.setBackground(dot);
        android.widget.FrameLayout.LayoutParams dlp = new android.widget.FrameLayout.LayoutParams(
                CaseViews.dp(this, 9), CaseViews.dp(this, 9),
                android.view.Gravity.TOP | android.view.Gravity.END);
        dlp.topMargin = CaseViews.dp(this, 6);
        dlp.rightMargin = CaseViews.dp(this, 6);
        box.addView(filterDot, dlp);
        filterDot.setVisibility(View.GONE);
        box.setContentDescription("Filters");
        box.setOnClickListener(v -> showFilters());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                CaseViews.dp(this, 48), CaseViews.dp(this, 48));
        lp.leftMargin = CaseViews.dp(this, 8);
        box.setLayoutParams(lp);
        return box;
    }

    private int activeFilters() {
        int n = 0;
        if (!fCase.isEmpty()) n++;
        if (!fSub.isEmpty()) n++;
        if (!fSubSub.isEmpty()) n++;
        if (!fCourt.isEmpty()) n++;
        if (fYear != 0) n++;
        if (!fLawyer.isEmpty()) n++;
        if (!fTopic.isEmpty()) n++;
        return n;
    }

    // ---------- filter popup: Reset (top right), Cancel + Apply (bottom) ----------

    private void showFilters() {
        final android.app.Dialog d = new android.app.Dialog(this);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);

        // draft values: only copied into the applied fields on Apply
        final String[] topic = {fTopic};
        final List<TextView> chipViews = new ArrayList<>();

        final Spinner caseSp = spinner(CaseOptions.arr(opts.types()));
        final Spinner subSp = spinner(CaseOptions.arr(opts.allSubs()));
        final Spinner subSubSp = spinner(CaseOptions.arr(opts.allSubSubs("")));
        final Spinner courtSp = spinner(CaseOptions.arr(opts.courts()));
        final Spinner yearSp = spinner(years());
        final AutoCompleteTextView lawyer = new RoundAuto(this);
        FieldStyle.hint(lawyer, "Lawyer name");
        lawyer.setSingleLine(true);
        lawyer.setThreshold(1);
        lawyer.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, details.lawyers()));

        // fill the dependent lists for the applied case type / sub type, then select the applied values
        fillSubs(subSp, fCase);
        fillSubSubs(subSubSp, fCase, fSub);
        select(caseSp, fCase);
        select(subSp, fSub);
        select(subSubSp, fSubSub);
        select(courtSp, fCourt);
        select(yearSp, fYear == 0 ? "" : String.valueOf(fYear));
        lawyer.setText(fLawyer);

        onUserPick(caseSp, () -> {
            fillSubs(subSp, val(caseSp));
            fillSubSubs(subSubSp, val(caseSp), "");
        });
        onUserPick(subSp, () -> fillSubSubs(subSubSp, val(caseSp), val(subSp)));

        // ----- layout -----
        int pad = CaseViews.dp(this, 16);
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        float r = CaseViews.dp(this, 22);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(AppTheme.bg(this));
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(bg);

        // header: title + Reset
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        head.setPadding(pad, pad, pad, CaseViews.dp(this, 10));
        head.addView(CaseViews.text(this, "Filters", 18, AppTheme.text(this), true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView reset = CaseViews.text(this, "Reset", 15, AppTheme.accent(this), true);
        reset.setPadding(CaseViews.dp(this, 12), CaseViews.dp(this, 6), 0, CaseViews.dp(this, 6));
        head.addView(reset);
        sheet.addView(head);
        View line = new View(this);
        line.setBackgroundColor(AppTheme.cardStroke(this));
        sheet.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));

        // body (scrolls)
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, 0, pad, pad);
        body.addView(heading("Marked category"));
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(0, CaseViews.dp(this, 8), 0, 0);
        final Runnable styleChips = () -> {
            for (TextView t : chipViews) {
                boolean on = topic[0].equals(t.getTag());
                t.setTextColor(on ? Color.WHITE : AppTheme.text(this));
                t.setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
                t.setBackground(on
                        ? CaseViews.box(this, AppTheme.isDark(this) ? 0xFF3F51B5 : CaseViews.NAVY, 18, 0, 0)
                        : CaseViews.box(this, AppTheme.card(this), 18, AppTheme.fieldStroke(this), 1));
            }
        };
        java.util.List<String> catList = new java.util.ArrayList<>();
        catList.add("");
        java.util.Collections.addAll(catList, MarkStore.SUBS);
        java.util.TreeSet<String> used = new java.util.TreeSet<>();
        for (Map<Integer, String> m : marks.everything().values()) used.addAll(m.values());
        for (String u : used) if (!catList.contains(u)) catList.add(u);
        String[] cats = catList.toArray(new String[0]);
        for (String c : cats) {
            TextView t = new TextView(this);
            t.setText(c.isEmpty() ? "All" : c);
            t.setTextSize(13);
            t.setTag(c);
            t.setPadding(CaseViews.dp(this, 14), CaseViews.dp(this, 8),
                    CaseViews.dp(this, 14), CaseViews.dp(this, 8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = CaseViews.dp(this, 8);
            t.setOnClickListener(v -> { topic[0] = (String) v.getTag(); styleChips.run(); });
            chipViews.add(t);
            chipRow.addView(t, lp);
        }
        chipScroll.addView(chipRow);
        body.addView(chipScroll);
        styleChips.run();

        body.addView(heading("Case"));
        body.addView(pair("Case type", caseSp, "Sub case type", subSp));
        body.addView(full("Sub-sub case type", subSubSp));
        body.addView(pair("Court", courtSp, "Year of filing", yearSp));
        body.addView(heading("Lawyer"));
        body.addView(full("Lawyer", lawyer));

        ScrollView sv = new ScrollView(this);
        sv.addView(body);
        sheet.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // footer: Cancel + Apply
        View line2 = new View(this);
        line2.setBackgroundColor(AppTheme.cardStroke(this));
        sheet.addView(line2, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setPadding(pad, CaseViews.dp(this, 12), pad, CaseViews.dp(this, 14));
        Button cancel = LegalUi.outlineButton(this, "Cancel");
        Button apply = new Button(this);
        apply.setText("Apply");
        apply.setAllCaps(false);
        apply.setTextColor(Color.WHITE);
        apply.setBackground(CaseViews.box(this, AppTheme.isDark(this) ? 0xFF3F51B5 : CaseViews.NAVY, 24, 0, 0));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, CaseViews.dp(this, 48), 1f);
        half.rightMargin = CaseViews.dp(this, 6);
        foot.addView(cancel, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, CaseViews.dp(this, 48), 1f);
        half2.leftMargin = CaseViews.dp(this, 6);
        foot.addView(apply, half2);
        sheet.addView(foot);

        reset.setOnClickListener(v -> {
            topic[0] = "";
            styleChips.run();
            caseSp.setSelection(0);
            fillSubs(subSp, "");
            fillSubSubs(subSubSp, "", "");
            subSp.setSelection(0);
            subSubSp.setSelection(0);
            courtSp.setSelection(0);
            yearSp.setSelection(0);
            lawyer.setText("");
        });
        cancel.setOnClickListener(v -> d.dismiss());
        apply.setOnClickListener(v -> {
            fCase = val(caseSp);
            fSub = val(subSp);
            fSubSub = val(subSubSp);
            fCourt = val(courtSp);
            String y = val(yearSp);
            fYear = y.isEmpty() ? 0 : Integer.parseInt(y);
            fLawyer = lawyer.getText().toString().trim();
            fTopic = topic[0];
            d.dismiss();
            refresh();
        });

        int h = (int) (getResources().getDisplayMetrics().heightPixels * 0.86f);
        d.setContentView(sheet, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, h));
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, h);
            w.setGravity(android.view.Gravity.BOTTOM);
            w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        d.show();
    }

    private TextView heading(String s) {
        TextView t = CaseViews.text(this, s, 15, AppTheme.text(this), true);
        t.setPadding(0, CaseViews.dp(this, 18), 0, 0);
        return t;
    }

    private void fillSubs(Spinner subSp, String caseType) {
        String[] subs = CaseOptions.arr(caseType.isEmpty() ? opts.allSubs() : opts.subs(caseType));
        subSp.setAdapter(CaseViews.wrapAdapter(this, withAll(subs)));
    }

    private void fillSubSubs(Spinner subSubSp, String caseType, String sub) {
        // sub = All -> every sub-sub of the chosen type, or civil + criminal together
        List<String> l = sub.isEmpty() ? opts.allSubSubs(caseType)
                : (caseType.isEmpty() ? opts.subSubsAnyType(sub) : opts.subSubs(caseType, sub));
        subSubSp.setAdapter(CaseViews.wrapAdapter(this, withAll(CaseOptions.arr(l))));
    }

    /** Select the item with this text; "" or not found = All. */
    private void select(Spinner s, String value) {
        if (value.isEmpty()) { s.setSelection(0); return; }
        for (int i = 0; i < s.getAdapter().getCount(); i++) {
            if (value.equals(String.valueOf(s.getAdapter().getItem(i)))) { s.setSelection(i); return; }
        }
        s.setSelection(0);
    }

    /** Runs the action when the user picks something; ignores the automatic first callback. */
    private void onUserPick(Spinner s, Runnable action) {
        final boolean[] first = {true};
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (first[0]) { first[0] = false; return; }
                action.run();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    // ---------- sort ----------

    private void updateSortLabel() {
        sortBtn.setText("Sort: " + SORTS[sortIdx] + "  \u25BE");
    }

    private void chooseSort() {
        PickerDialogs.optionSheet(this, "Sort by", null, java.util.Arrays.asList(SORTS), sortIdx, which -> {
            sortIdx = which;
            updateSortLabel();
            refresh();
        });
    }

    @Override
    protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        smartSearch.onActivityResult(req, res, data);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private String[] withAll(String[] items) {
        String[] out = new String[items.length + 1];
        out[0] = ALL;
        System.arraycopy(items, 0, out, 1, items.length);
        return out;
    }

    private Spinner spinner(String[] items) {
        Spinner s = new RoundSpinner(this);
        s.setAdapter(CaseViews.wrapAdapter(this, withAll(items)));
        return s;
    }

    private String[] years() {
        int now = Calendar.getInstance().get(Calendar.YEAR);
        String[] out = new String[now - 1950 + 1];
        for (int y = now, i = 0; y >= 1950; y--, i++) out[i] = String.valueOf(y);
        return out;
    }

    // one labelled field, full width (long option names stay readable)
    private LinearLayout full(String la, View v) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(label(la));
        col.addView(v, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return col;
    }

    // two labelled fields side by side
    private LinearLayout pair(String la, View a, String lb, View b) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int gap = CaseViews.dp(this, 8);
        for (int i = 0; i < 2; i++) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(label(i == 0 ? la : lb));
            col.addView(i == 0 ? a : b, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i == 0) lp.rightMargin = gap;
            row.addView(col, lp);
        }
        return row;
    }

    private TextView label(String text) {
        return CaseViews.fieldLabel(this, text);
    }

    private String val(Spinner s) {
        Object o = s.getSelectedItem();
        if (o == null || ALL.equals(o)) return "";
        return o.toString();
    }

    // sorted 0-based pages -> "1-3, 7" (1-based)
    private static String ranges(List<Integer> pages) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < pages.size()) {
            int start = pages.get(i);
            int end = start;
            while (i + 1 < pages.size() && pages.get(i + 1) == end + 1) {
                end = pages.get(++i);
            }
            if (sb.length() > 0) sb.append(", ");
            if (start == end) sb.append(start + 1);
            else sb.append(start + 1).append('-').append(end + 1);
            i++;
        }
        return sb.toString();
    }

    private void refresh() {
        if (status == null) return;

        String topic = fTopic;
        String ct = fCase;
        String sub = fSub;
        String subSub = fSubSub;
        String court = fCourt;
        int year = fYear;
        String lawyerQ = fLawyer.toLowerCase(Locale.ROOT);
        String fileQ = fileEdit.getText().toString().trim().toLowerCase(Locale.ROOT);
        boolean detailFilter = !ct.isEmpty() || !sub.isEmpty() || !subSub.isEmpty() || !court.isEmpty()
                || year != 0 || !lawyerQ.isEmpty();
        final int sortMode = sortIdx;

        // Disk/prefs work runs off the UI thread so the page appears immediately;
        // gen drops results from a refresh that was superseded while it ran.
        final int gen = ++refreshGen;
        new Thread(() -> {
        Map<String, Map<Integer, String>> allMarks = marks.everything();
        Map<String, DetailsStore.Details> allDetails = details.everything();

        TreeSet<String> paths = new TreeSet<>();
        for (Map.Entry<String, Map<Integer, String>> e : allMarks.entrySet()) {
            if (e.getValue() != null && !e.getValue().isEmpty()) paths.add(e.getKey());
        }

        List<String> sorted = new ArrayList<>();
        for (String p : paths) {
            File f = new File(p);
            if (!f.exists()) continue;
            if (!fileQ.isEmpty()) {
                DetailsStore.Details dq = allDetails.get(p);
                String hay = f.getName() + " " + (dq == null ? "" : dq.caseName);
                if (!hay.toLowerCase(Locale.ROOT).contains(fileQ)) continue;
            }
            sorted.add(p);
        }
        Collections.sort(sorted, (a, b) ->
                new File(a).getName().compareToIgnoreCase(new File(b).getName()));

        final List<Item> out = new ArrayList<>();
        for (String p : sorted) {
            // details filters
            DetailsStore.Details d = allDetails.get(p);
            if (detailFilter) {
                if (d == null) continue;
                if (!ct.isEmpty() && !ct.equals(d.caseType)) continue;
                if (!sub.isEmpty() && !sub.equals(d.subType)) continue;
                if (!subSub.isEmpty() && !subSub.equals(d.subSubType)) continue;
                if (!court.isEmpty() && !court.equals(d.court)) continue;
                if (year != 0 && year != d.year) continue;
                if (!lawyerQ.isEmpty()
                        && !d.lawyer.toLowerCase(Locale.ROOT).contains(lawyerQ)) continue;
            }

            // topic filter
            Map<String, List<Integer>> byCat = new TreeMap<>();
            Map<Integer, String> m = allMarks.get(p);
            if (m != null) {
                for (Map.Entry<Integer, String> e : m.entrySet()) {
                    if (!topic.isEmpty() && !topic.equals(e.getValue())) continue;
                    if (!byCat.containsKey(e.getValue())) byCat.put(e.getValue(), new ArrayList<>());
                    byCat.get(e.getValue()).add(e.getKey());
                }
            }
            if (!topic.isEmpty() && byCat.isEmpty()) continue;

            StringBuilder tb = new StringBuilder();
            for (Map.Entry<String, List<Integer>> e : byCat.entrySet()) {
                Collections.sort(e.getValue());
                if (tb.length() > 0) tb.append("\n");
                tb.append(e.getKey()).append(": pages ").append(ranges(e.getValue()));
            }
            String dt = d == null ? "" : DetailsStore.summary(d);
            Item item = new Item(p, tb.toString(), dt, topic);
            item.caseName = d == null ? "" : d.caseName;
            item.year = d == null ? 0 : d.year;
            item.marked = m == null ? 0 : m.size();
            item.modified = new File(p).lastModified();
            out.add(item);
        }
        Collections.sort(out, (x, y) -> {
            String xn = new File(x.path).getName(), yn = new File(y.path).getName();
            switch (sortMode) {
                case 1: return yn.compareToIgnoreCase(xn);
                case 2: {
                    int c = x.caseName.compareToIgnoreCase(y.caseName);
                    if (x.caseName.isEmpty() != y.caseName.isEmpty()) return x.caseName.isEmpty() ? 1 : -1;
                    return c != 0 ? c : xn.compareToIgnoreCase(yn);
                }
                case 3: return y.year != x.year ? Integer.compare(y.year, x.year) : xn.compareToIgnoreCase(yn);
                case 4: {
                    if ((x.year == 0) != (y.year == 0)) return x.year == 0 ? 1 : -1;
                    return x.year != y.year ? Integer.compare(x.year, y.year) : xn.compareToIgnoreCase(yn);
                }
                case 5: return y.marked != x.marked ? Integer.compare(y.marked, x.marked) : xn.compareToIgnoreCase(yn);
                case 6: return Long.compare(y.modified, x.modified);
                default: return xn.compareToIgnoreCase(yn);
            }
        });
        runOnUiThread(() -> {
            if (gen != refreshGen || isFinishing()) return;
            items.clear();
            items.addAll(out);
            int nf = activeFilters();
            status.setText(items.size() + (items.size() == 1 ? " marked file" : " marked files")
                    + (nf > 0 ? "  \u00B7  " + nf + (nf == 1 ? " filter" : " filters") : ""));
            filterDot.setVisibility(nf > 0 ? View.VISIBLE : View.GONE);
            adapter.notifyDataSetChanged();
        });
        }).start();
    }

    private volatile int refreshGen = 0;

    private class ItemAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            Item it = items.get(i);
            LinearLayout row = new LinearLayout(AdvancedSearchActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(16, 20, 16, 20);

            TextView name = new TextView(AdvancedSearchActivity.this);
            name.setText(new File(it.path).getName());
            name.setTextSize(16);
            name.setTypeface(null, Typeface.BOLD);
            name.setTextColor(AppTheme.text(AdvancedSearchActivity.this));

            TextView path = new TextView(AdvancedSearchActivity.this);
            path.setText(it.path);
            path.setTextSize(12);
            path.setTextColor(Color.GRAY);

            row.addView(name);
            row.addView(path);

            if (!it.topics.isEmpty()) {
                TextView t = new TextView(AdvancedSearchActivity.this);
                t.setText(it.topics);
                t.setTextSize(13);
                t.setTextColor(AppTheme.topics(AdvancedSearchActivity.this));
                row.addView(t);
            }
            if (!it.details.isEmpty()) {
                TextView t = new TextView(AdvancedSearchActivity.this);
                t.setText(it.details);
                t.setTextSize(13);
                t.setTextColor(AppTheme.info(AdvancedSearchActivity.this));
                row.addView(t);
            }
            return row;
        }
    }
}
