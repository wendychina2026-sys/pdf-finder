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
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class AdvancedSearchActivity extends Activity {

    private static final String ALL = "All";

    static class Item {
        final String path;
        final String topics;
        final String details;
        final String topic;   // selected topic filter, "" = all

        Item(String path, String topics, String details, String topic) {
            this.path = path;
            this.topics = topics;
            this.details = details;
            this.topic = topic;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private BaseAdapter adapter;
    private Spinner topicSpin;
    private Spinner caseSpin;
    private Spinner subSpin;
    private Spinner subSubSpin;
    private CaseOptions opts;
    private Spinner courtSpin;
    private Spinner yearSpin;
    private AutoCompleteTextView lawyerEdit;
    private EditText fileEdit;
    private TextView status;
    private ScrollView filterScroll;
    private MarkStore marks;
    private DetailsStore details;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Advanced search");
        marks = new MarkStore(this);
        details = new DetailsStore(this);
        opts = new CaseOptions(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);

        // ----- filters (collapsible) -----
        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.VERTICAL);

        topicSpin = spinner(MarkStore.SUBS);
        caseSpin = spinner(CaseOptions.arr(opts.types()));
        subSpin = spinner(CaseOptions.arr(opts.allSubs()));
        subSubSpin = spinner(new String[0]);
        courtSpin = spinner(CaseOptions.arr(opts.courts()));
        yearSpin = spinner(years());

        lawyerEdit = new AutoCompleteTextView(this);
        lawyerEdit.setHint("Lawyer name");
        lawyerEdit.setSingleLine(true);
        lawyerEdit.setThreshold(1);
        lawyerEdit.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, details.lawyers()));

        fileEdit = new EditText(this);
        fileEdit.setHint("File name");
        fileEdit.setSingleLine(true);

        filters.addView(label("Case type"));
        filters.addView(caseSpin);
        filters.addView(label("Sub case type"));
        filters.addView(subSpin);
        filters.addView(label("Sub-sub case type"));
        filters.addView(subSubSpin);
        filters.addView(label("Topic (marked category)"));
        filters.addView(topicSpin);
        filters.addView(label("Court"));
        filters.addView(courtSpin);
        filters.addView(label("Year of filing"));
        filters.addView(yearSpin);
        filters.addView(label("Lawyer"));
        filters.addView(lawyerEdit);
        filters.addView(label("File name"));
        filters.addView(fileEdit);

        filterScroll = new ScrollView(this);
        filterScroll.addView(filters);

        AdapterView.OnItemSelectedListener refresher = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { refresh(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        };
        topicSpin.setOnItemSelectedListener(refresher);
        subSubSpin.setOnItemSelectedListener(refresher);
        subSpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                String ct = val(caseSpin);
                String sb = val(subSpin);
                java.util.List<String> l = sb.isEmpty() ? new ArrayList<String>()
                        : (ct.isEmpty() ? opts.subSubsAnyType(sb) : opts.subSubs(ct, sb));
                subSubSpin.setAdapter(new ArrayAdapter<>(AdvancedSearchActivity.this,
                        android.R.layout.simple_spinner_dropdown_item,
                        withAll(CaseOptions.arr(l))));
                refresh();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        courtSpin.setOnItemSelectedListener(refresher);
        yearSpin.setOnItemSelectedListener(refresher);
        caseSpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                String ct = val(caseSpin);
                String[] subs = CaseOptions.arr(ct.isEmpty() ? opts.allSubs() : opts.subs(ct));
                subSpin.setAdapter(new ArrayAdapter<>(AdvancedSearchActivity.this,
                        android.R.layout.simple_spinner_dropdown_item, withAll(subs)));
                refresh();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { refresh(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        lawyerEdit.addTextChangedListener(watcher);
        fileEdit.addTextChangedListener(watcher);

        status = new TextView(this);
        status.setPadding(0, 8, 0, 8);

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
        root.addView(filterScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(status, wide);
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 2f));
        setContentView(root);
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
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, withAll(items)));
        return s;
    }

    private String[] years() {
        int now = Calendar.getInstance().get(Calendar.YEAR);
        String[] out = new String[now - 1950 + 1];
        for (int y = now, i = 0; y >= 1950; y--, i++) out[i] = String.valueOf(y);
        return out;
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setPadding(0, 16, 0, 0);
        return t;
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
        if (status == null || topicSpin.getSelectedItem() == null) return;

        String topic = val(topicSpin);
        String ct = val(caseSpin);
        String sub = val(subSpin);
        String subSub = val(subSubSpin);
        String court = val(courtSpin);
        String yearStr = val(yearSpin);
        int year = yearStr.isEmpty() ? 0 : Integer.parseInt(yearStr);
        String lawyerQ = lawyerEdit.getText().toString().trim().toLowerCase(Locale.ROOT);
        String fileQ = fileEdit.getText().toString().trim().toLowerCase(Locale.ROOT);
        boolean detailFilter = !ct.isEmpty() || !sub.isEmpty() || !subSub.isEmpty() || !court.isEmpty()
                || year != 0 || !lawyerQ.isEmpty();

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
            if (!fileQ.isEmpty() && !f.getName().toLowerCase(Locale.ROOT).contains(fileQ)) continue;
            sorted.add(p);
        }
        Collections.sort(sorted, (a, b) ->
                new File(a).getName().compareToIgnoreCase(new File(b).getName()));

        items.clear();
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
            items.add(new Item(p, tb.toString(), dt, topic));
        }
        status.setText(items.size() + (items.size() == 1 ? " file" : " files"));
        adapter.notifyDataSetChanged();
    }

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
            name.setTextColor(Color.BLACK);

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
                t.setTextColor(0xFF2E7D32);
                row.addView(t);
            }
            if (!it.details.isEmpty()) {
                TextView t = new TextView(AdvancedSearchActivity.this);
                t.setText(it.details);
                t.setTextSize(13);
                t.setTextColor(0xFF1565C0);
                row.addView(t);
            }
            return row;
        }
    }
}
