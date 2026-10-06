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
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class CategorySearchActivity extends Activity {

    private static final String ALL = "All categories";

    static class Item {
        final String path;
        final String detail;

        Item(String path, String detail) {
            this.path = path;
            this.detail = detail;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private BaseAdapter adapter;
    private Spinner spinner;
    private EditText search;
    private TextView status;
    private MarkStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Search by category");
        store = new MarkStore(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);

        String[] options = new String[MarkStore.SUBS.length + 1];
        options[0] = ALL;
        System.arraycopy(MarkStore.SUBS, 0, options, 1, MarkStore.SUBS.length);
        spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, options));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                refresh();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        search = new EditText(this);
        search.setHint("Search file name");
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { refresh(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        status = new TextView(this);
        status.setPadding(0, 8, 0, 12);

        adapter = new ItemAdapter();
        ListView list = new ListView(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) -> {
            Intent i = new Intent(this, MarkActivity.class);
            i.putExtra("path", items.get(pos).path);
            startActivity(i);
        });

        LinearLayout.LayoutParams wide = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        root.addView(spinner, wide);
        root.addView(search, wide);
        root.addView(status, wide);
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (spinner == null || spinner.getSelectedItem() == null) return;
        String cat = (String) spinner.getSelectedItem();
        boolean all = cat.equals(ALL);
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);

        Map<String, Map<Integer, String>> data = store.everything();
        List<String> paths = new ArrayList<>();
        for (String p : data.keySet()) {
            File f = new File(p);
            if (f.exists() && (q.isEmpty() || f.getName().toLowerCase(Locale.ROOT).contains(q))) {
                paths.add(p);
            }
        }
        Collections.sort(paths, (a, b) ->
                new File(a).getName().compareToIgnoreCase(new File(b).getName()));

        items.clear();
        for (String p : paths) {
            Map<String, List<Integer>> byCat = new TreeMap<>();
            for (Map.Entry<Integer, String> e : data.get(p).entrySet()) {
                if (!all && !e.getValue().equals(cat)) continue;
                if (!byCat.containsKey(e.getValue())) byCat.put(e.getValue(), new ArrayList<>());
                byCat.get(e.getValue()).add(e.getKey() + 1);
            }
            if (byCat.isEmpty()) continue;

            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, List<Integer>> e : byCat.entrySet()) {
                Collections.sort(e.getValue());
                if (sb.length() > 0) sb.append("\n");
                sb.append(e.getKey()).append(": pages ");
                for (int i = 0; i < e.getValue().size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(e.getValue().get(i));
                }
            }
            items.add(new Item(p, sb.toString()));
        }
        status.setText(items.size() + " marked files");
        adapter.notifyDataSetChanged();
    }

    private class ItemAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            Item it = items.get(i);
            LinearLayout row = new LinearLayout(CategorySearchActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(16, 20, 16, 20);

            TextView name = new TextView(CategorySearchActivity.this);
            name.setText(new File(it.path).getName());
            name.setTextSize(16);
            name.setTypeface(null, Typeface.BOLD);
            name.setTextColor(Color.BLACK);

            TextView path = new TextView(CategorySearchActivity.this);
            path.setText(it.path);
            path.setTextSize(12);
            path.setTextColor(Color.GRAY);

            TextView detail = new TextView(CategorySearchActivity.this);
            detail.setText(it.detail);
            detail.setTextSize(13);
            detail.setTextColor(0xFF2E7D32);

            row.addView(name);
            row.addView(path);
            row.addView(detail);
            return row;
        }
    }
}
