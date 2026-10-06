package com.example.twobuttons;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class PdfAdapter extends ArrayAdapter<Pdf> {

    final Set<String> selected = new LinkedHashSet<>();
    boolean selectionMode = false;

    PdfAdapter(Context c) {
        super(c, 0, new ArrayList<Pdf>());
    }

    // Rebuild visible list from master list + search text. Returns visible count.
    int filter(List<Pdf> all, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        clear();
        Set<String> visible = new HashSet<>();
        for (Pdf p : all) {
            if (q.isEmpty() || p.name.toLowerCase(Locale.ROOT).contains(q)) {
                add(p);
                visible.add(p.path);
            }
        }
        selected.retainAll(visible); // hidden files never stay selected
        return getCount();
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

    // Row: [checkbox] name (bold) / path (small grey)
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
}
