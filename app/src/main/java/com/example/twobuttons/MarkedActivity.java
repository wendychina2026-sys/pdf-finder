package com.example.twobuttons;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MarkedActivity extends Activity {

    static class Entry {
        final boolean header;
        final String path;
        final int page;      // 0-based
        final String sub;
        boolean checked = true;

        Entry(boolean header, String path, int page, String sub) {
            this.header = header;
            this.path = path;
            this.page = page;
            this.sub = sub;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private BaseAdapter adapter;
    private TextView status;
    private Button extractButton;
    private MarkStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Marked pages");
        store = new MarkStore(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button toggleAll = new Button(this);
        toggleAll.setText("Check all / none");
        toggleAll.setOnClickListener(v -> {
            boolean anyOff = false;
            for (Entry e : entries) if (!e.header && !e.checked) anyOff = true;
            for (Entry e : entries) if (!e.header) e.checked = anyOff;
            adapter.notifyDataSetChanged();
        });
        extractButton = new Button(this);
        extractButton.setText("Extract checked");
        extractButton.setOnClickListener(v -> extractChecked());
        buttons.addView(toggleAll, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        buttons.addView(extractButton, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        status = new TextView(this);
        status.setPadding(0, 12, 0, 12);

        adapter = new EntryAdapter();
        ListView list = new ListView(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) -> {
            Entry e = entries.get(pos);
            if (e.header) {
                Intent i = new Intent(this, MarkActivity.class);
                i.putExtra("path", e.path);
                startActivity(i);
            } else {
                e.checked = !e.checked;
                adapter.notifyDataSetChanged();
            }
        });

        LinearLayout.LayoutParams wide = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        root.addView(buttons, wide);
        root.addView(status, wide);
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        entries.clear();
        Map<String, Map<Integer, String>> all = store.everything();
        List<String> paths = new ArrayList<>();
        for (String p : all.keySet()) if (new File(p).exists()) paths.add(p);
        Collections.sort(paths, (a, b) ->
                new File(a).getName().compareToIgnoreCase(new File(b).getName()));

        int n = 0;
        for (String p : paths) {
            entries.add(new Entry(true, p, -1, null));
            List<Integer> pages = new ArrayList<>(all.get(p).keySet());
            Collections.sort(pages);
            for (int pg : pages) {
                entries.add(new Entry(false, p, pg, all.get(p).get(pg)));
                n++;
            }
        }
        status.setText(n + " marked pages in " + paths.size() + " files");
        adapter.notifyDataSetChanged();
    }

    private void extractChecked() {
        // one output PDF per (file, category)
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        for (Entry e : entries) {
            if (e.header || !e.checked) continue;
            String key = e.path + "\n" + e.sub;
            if (!groups.containsKey(key)) groups.put(key, new ArrayList<>());
            groups.get(key).add(e.page);
        }
        if (groups.isEmpty()) {
            Toast.makeText(this, "Nothing checked", Toast.LENGTH_SHORT).show();
            return;
        }
        extractButton.setEnabled(false);
        status.setText("Extracting...");

        new Thread(() -> {
            int ok = 0;
            String error = null;
            for (Map.Entry<String, List<Integer>> g : groups.entrySet()) {
                String[] k = g.getKey().split("\n", 2);
                try {
                    PageExtractor.extract(this, k[0], g.getValue(), k[1]);
                    ok++;
                } catch (Throwable t) {
                    error = new File(k[0]).getName() + ": " + t;
                }
            }
            final String msg = "Saved " + ok + " file(s) in Documents/PDFFinder"
                    + (error != null ? "\nError: " + error : "");
            runOnUiThread(() -> {
                extractButton.setEnabled(true);
                status.setText(msg);
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private class EntryAdapter extends BaseAdapter {
        @Override public int getCount() { return entries.size(); }
        @Override public Object getItem(int i) { return entries.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            Entry e = entries.get(i);
            LinearLayout row = new LinearLayout(MarkedActivity.this);
            if (e.header) {
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(16, 20, 16, 20);
                row.setBackgroundColor(0xFFEEEEEE);
                TextView name = new TextView(MarkedActivity.this);
                name.setText(new File(e.path).getName());
                name.setTextSize(16);
                name.setTypeface(null, Typeface.BOLD);
                name.setTextColor(Color.BLACK);
                TextView path = new TextView(MarkedActivity.this);
                path.setText(e.path);
                path.setTextSize(12);
                path.setTextColor(Color.GRAY);
                row.addView(name);
                row.addView(path);
            } else {
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(48, 12, 16, 12);
                CheckBox box = new CheckBox(MarkedActivity.this);
                box.setChecked(e.checked);
                box.setFocusable(false);
                box.setClickable(false);
                TextView t = new TextView(MarkedActivity.this);
                t.setText("Page " + (e.page + 1) + "  -  " + e.sub);
                t.setTextSize(15);
                row.addView(box);
                row.addView(t);
            }
            return row;
        }
    }
}
