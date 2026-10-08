package com.example.twobuttons;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

public class MarkedFilesActivity extends BaseActivity {

    static class Item {
        final String path;
        final Map<Integer, String> marks;

        Item(String path, Map<Integer, String> marks) {
            this.path = path;
            this.marks = marks;
        }
    }

    private final List<Item> all = new ArrayList<>();
    private final List<Item> items = new ArrayList<>();
    private BaseAdapter adapter;
    private TextView status;
    private EditText search;
    private MarkStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Marked PDFs");
        store = new MarkStore(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);

        search = new EditText(this);
        search.setHint("Search file name, path or category");
        search.setSingleLine(true);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { applyFilter(); }
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

        root.addView(search, new LinearLayout.LayoutParams(
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
        loadAll();
        applyFilter();
    }

    private void loadAll() {
        all.clear();
        Map<String, Map<Integer, String>> everything = store.everything();
        List<String> paths = new ArrayList<>();
        for (String p : everything.keySet()) {
            Map<Integer, String> m = everything.get(p);
            if (new File(p).exists() && m != null && !m.isEmpty()) paths.add(p);
        }
        Collections.sort(paths, (a, b) ->
                new File(a).getName().compareToIgnoreCase(new File(b).getName()));
        for (String p : paths) all.add(new Item(p, everything.get(p)));
    }

    private void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        items.clear();
        for (Item it : all) {
            if (q.isEmpty() || matches(it, q)) items.add(it);
        }
        status.setText(items.size() + " marked PDFs");
        adapter.notifyDataSetChanged();
    }

    private boolean matches(Item it, String q) {
        if (new File(it.path).getName().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (it.path.toLowerCase(Locale.ROOT).contains(q)) return true;
        for (String sub : it.marks.values()) {
            if (sub.toLowerCase(Locale.ROOT).contains(q)) return true;
        }
        return false;
    }

    private int totalPages(String path) {
        try (ParcelFileDescriptor pfd =
                     ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer r = new PdfRenderer(pfd)) {
            return r.getPageCount();
        } catch (Exception e) {
            return -1;
        }
    }

    // sorted 0-based pages -> "1-3, 7, 9-10" (1-based)
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

    private void showDetails(Item it) {
        int total = totalPages(it.path);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 24, 48, 24);

        // keep MarkStore.SUBS order, then any unknown names
        List<String> order = new ArrayList<>();
        Collections.addAll(order, MarkStore.SUBS);
        for (String s : new TreeSet<>(it.marks.values())) if (!order.contains(s)) order.add(s);

        final AlertDialog[] holder = new AlertDialog[1];

        for (String sub : order) {
            final List<Integer> pages = new ArrayList<>();
            for (Map.Entry<Integer, String> e : it.marks.entrySet()) {
                if (sub.equals(e.getValue())) pages.add(e.getKey());
            }
            if (pages.isEmpty()) continue;
            Collections.sort(pages);
            final String subName = sub;
            final String rangeText = ranges(pages);

            TextView t = new TextView(this);
            t.setText(sub + " (" + pages.size() + (pages.size() == 1 ? " page)" : " pages)")
                    + "\n  Pages: " + rangeText);
            t.setTextSize(15);
            t.setTextColor(AppTheme.text(this));
            t.setPadding(0, 16, 0, 0);

            LinearLayout btns = new LinearLayout(this);
            btns.setOrientation(LinearLayout.HORIZONTAL);

            Button view = new Button(this);
            view.setText("View");
            view.setAllCaps(false);
            view.setOnClickListener(v -> {
                Intent i = new Intent(this, MarkActivity.class);
                i.putExtra("path", it.path);
                i.putExtra("only_sub", subName);
                startActivity(i);
                if (holder[0] != null) holder[0].dismiss();
            });

            Button del = new Button(this);
            del.setText("Delete");
            del.setAllCaps(false);
            del.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Unmark " + subName + "?")
                    .setMessage("Remove " + subName + " mark from pages " + rangeText + "?")
                    .setPositiveButton("Delete", (d, w) -> {
                        for (int pg : pages) store.remove(it.path, pg);
                        if (holder[0] != null) holder[0].dismiss();
                        loadAll();
                        applyFilter();
                        for (Item n : all) {
                            if (n.path.equals(it.path)) {
                                showDetails(n);
                                break;
                            }
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show());

            btns.addView(view);
            btns.addView(del);
            box.addView(t);
            box.addView(btns);
        }

        int marked = it.marks.size();
        StringBuilder sb = new StringBuilder();
        sb.append("Marked pages: ").append(marked).append('\n');
        if (total >= 0) {
            sb.append("Unmarked pages: ").append(Math.max(0, total - marked)).append('\n');
            sb.append("Total pages: ").append(total);
        } else {
            sb.append("Total pages: unknown (cannot read PDF)");
        }
        TextView sum = new TextView(this);
        sum.setText(sb.toString());
        sum.setTextSize(15);
        sum.setTextColor(AppTheme.text(this));
        sum.setPadding(0, 24, 0, 0);
        box.addView(sum);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);

        holder[0] = new AlertDialog.Builder(this)
                .setTitle(new File(it.path).getName())
                .setView(sv)
                .setPositiveButton("Close", null)
                .show();
    }

    private class ItemAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            final Item it = items.get(i);
            LinearLayout row = new LinearLayout(MarkedFilesActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(16, 20, 16, 20);

            TextView name = new TextView(MarkedFilesActivity.this);
            name.setText(new File(it.path).getName());
            name.setTextSize(16);
            name.setTypeface(null, Typeface.BOLD);
            name.setTextColor(AppTheme.text(this));

            TextView path = new TextView(MarkedFilesActivity.this);
            path.setText(it.path);
            path.setTextSize(12);
            path.setTextColor(Color.GRAY);

            Button details = new Button(MarkedFilesActivity.this);
            details.setText("Show details");
            details.setAllCaps(false);
            details.setFocusable(false);
            details.setOnClickListener(v -> showDetails(it));

            row.addView(name);
            row.addView(path);
            row.addView(details, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            return row;
        }
    }
}
