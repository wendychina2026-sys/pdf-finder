package com.example.twobuttons;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MarkedFilesActivity extends Activity {

    static class Item {
        final String path;
        final int count;

        Item(String path, int count) {
            this.path = path;
            this.count = count;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private BaseAdapter adapter;
    private TextView status;
    private MarkStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Marked PDFs");
        store = new MarkStore(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);

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

        root.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        items.clear();
        Map<String, Map<Integer, String>> all = store.everything();
        List<String> paths = new ArrayList<>();
        for (String p : all.keySet()) if (new File(p).exists()) paths.add(p);
        Collections.sort(paths, (a, b) ->
                new File(a).getName().compareToIgnoreCase(new File(b).getName()));
        for (String p : paths) items.add(new Item(p, all.get(p).size()));
        status.setText(items.size() + " marked PDFs");
        adapter.notifyDataSetChanged();
    }

    private class ItemAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            Item it = items.get(i);
            LinearLayout row = new LinearLayout(MarkedFilesActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(16, 20, 16, 20);

            TextView name = new TextView(MarkedFilesActivity.this);
            name.setText(new File(it.path).getName());
            name.setTextSize(16);
            name.setTypeface(null, Typeface.BOLD);
            name.setTextColor(Color.BLACK);

            TextView path = new TextView(MarkedFilesActivity.this);
            path.setText(it.path);
            path.setTextSize(12);
            path.setTextColor(Color.GRAY);

            TextView count = new TextView(MarkedFilesActivity.this);
            count.setText(it.count + (it.count == 1 ? " page marked" : " pages marked"));
            count.setTextSize(13);
            count.setTextColor(0xFF2E7D32);

            row.addView(name);
            row.addView(path);
            row.addView(count);
            return row;
        }
    }
}
