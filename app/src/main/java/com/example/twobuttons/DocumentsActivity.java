package com.example.twobuttons;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Document Library page: search, filter chips, add / edit / delete. */
public class DocumentsActivity extends BaseActivity {

    private static final String[] FILTERS = {"All", "Petition", "Order", "Evidence", "Other"};

    private LegalStore store;
    private LinearLayout list;
    private LinearLayout chips;
    private EditText search;
    private String filter = "All";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Documents");
        store = new LegalStore(this);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(AppTheme.bg(this));

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = LegalUi.dp(this, 16);
        root.setPadding(p, p, p, p);

        root.addView(CaseViews.text(this, "Document Library", 22, AppTheme.text(this), true));
        TextView sub = CaseViews.text(this, "Browse case and client documents from one place.",
                13, AppTheme.sub(this), false);
        sub.setPadding(0, LegalUi.dp(this, 2), 0, LegalUi.dp(this, 12));
        root.addView(sub);

        search = new EditText(this);
        search.setHint("Search file, case or client");
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setPadding(LegalUi.dp(this, 14), LegalUi.dp(this, 12), LegalUi.dp(this, 14), LegalUi.dp(this, 12));
        search.setBackground(CaseViews.box(this, AppTheme.field(this), 10, AppTheme.fieldStroke(this), 1));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { renderList(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        root.addView(search, CaseViews.lp(this, 0, 10));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(chips);
        root.addView(hs, CaseViews.lp(this, 0, 12));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        sv.addView(root);
        page.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        Button add = new Button(this);
        add.setText("Add Document");
        add.setAllCaps(false);
        add.setTextSize(15);
        add.setTextColor(AppTheme.accent(this));
        add.setBackground(CaseViews.box(this, AppTheme.isDark(this) ? 0xFF2A2C44 : 0xFFBFE3FA, 24, 0, 0));
        add.setOnClickListener(v -> editDoc(null));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LegalUi.dp(this, 52));
        alp.setMargins(p, LegalUi.dp(this, 6), p, p);
        page.addView(add, alp);

        setContentView(page);
        renderChips();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderList();
    }

    private void renderChips() {
        chips.removeAllViews();
        for (String f : FILTERS) {
            boolean on = f.equals(filter);
            TextView t = CaseViews.text(this, (on ? "\u2713 " : "") + f, 14,
                    on ? AppTheme.accent(this) : AppTheme.text(this), false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(LegalUi.dp(this, 16), LegalUi.dp(this, 8), LegalUi.dp(this, 16), LegalUi.dp(this, 8));
            int fill = on ? (AppTheme.isDark(this) ? 0xFF2A2C44 : 0xFFBFE3FA) : AppTheme.field(this);
            t.setBackground(CaseViews.box(this, fill, 10, AppTheme.fieldStroke(this), on ? 0 : 1));
            t.setOnClickListener(v -> {
                filter = f;
                renderChips();
                renderList();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = LegalUi.dp(this, 8);
            chips.addView(t, lp);
        }
    }

    private void renderList() {
        if (list == null) return;
        list.removeAllViews();
        String q = search.getText().toString().trim().toLowerCase(Locale.US);
        int shown = 0;
        for (LegalStore.Doc d : store.docs()) {
            if (!"All".equals(filter) && !filter.equalsIgnoreCase(d.category)) continue;
            if (!q.isEmpty() && !(d.name.toLowerCase(Locale.US).contains(q)
                    || d.caseName.toLowerCase(Locale.US).contains(q)
                    || d.category.toLowerCase(Locale.US).contains(q))) continue;
            list.addView(row(d), CaseViews.lp(this, 0, 10));
            shown++;
        }
        if (shown == 0) list.addView(CaseViews.emptyNote(this, "No documents"));
    }

    private LinearLayout row(LegalStore.Doc d) {
        LinearLayout row = LegalUi.card(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        String ext = d.name.contains(".")
                ? d.name.substring(d.name.lastIndexOf('.') + 1).toLowerCase(Locale.US) : "";
        String label;
        int color;
        if (ext.equals("pdf")) { label = "PDF"; color = 0xFFD32F2F; }
        else if (ext.equals("doc") || ext.equals("docx")) { label = "DOC"; color = 0xFF1976D2; }
        else if (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png")) { label = ext.equals("png") ? "PNG" : "JPG"; color = 0xFF2E7D32; }
        else { label = ext.isEmpty() ? "FILE" : ext.toUpperCase(Locale.US); color = 0xFF6B6F80; }
        TextView badge = CaseViews.text(this, label, 12, color, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(CaseViews.box(this, (color & 0x00FFFFFF) | 0x22000000, 10, 0, 0));
        row.addView(badge, new LinearLayout.LayoutParams(LegalUi.dp(this, 52), LegalUi.dp(this, 52)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(LegalUi.dp(this, 14), 0, LegalUi.dp(this, 6), 0);
        col.addView(CaseViews.text(this, d.name, 15, AppTheme.text(this), true));
        col.addView(CaseViews.text(this, d.caseName, 13, AppTheme.sub(this), false));
        col.addView(CaseViews.text(this, d.size + "  \u2022  " + d.date, 13, AppTheme.sub(this), false));
        row.addView(col, LegalUi.weight());

        TextView more = CaseViews.text(this, "\u22EE", 24, AppTheme.text(this), true);
        more.setPadding(LegalUi.dp(this, 12), LegalUi.dp(this, 6), LegalUi.dp(this, 4), LegalUi.dp(this, 6));
        more.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(this, v);
            pm.getMenu().add(0, 1, 0, "Edit");
            pm.getMenu().add(0, 2, 1, "Delete");
            pm.setOnMenuItemClickListener(mi -> {
                if (mi.getItemId() == 1) editDoc(d);
                else LegalUi.confirm(this, "Delete " + d.name + "?", "This cannot be undone.", () -> {
                    store.deleteDoc(d.id);
                    renderList();
                });
                return true;
            });
            pm.show();
        });
        row.addView(more);
        row.setOnClickListener(v -> editDoc(d));
        return row;
    }

    private void editDoc(LegalStore.Doc existing) {
        LegalStore.Doc d = existing != null ? existing : new LegalStore.Doc();
        if (existing == null) d.date = new SimpleDateFormat("dd MMM yyyy", Locale.US).format(new Date());
        LinearLayout f = LegalUi.form(this);
        EditText name = LegalUi.text(this, f, "File name (with extension)", d.name);
        EditText cs = LegalUi.text(this, f, "Case / client", d.caseName);
        EditText size = LegalUi.text(this, f, "Size (e.g. 1.2 MB)", d.size);
        EditText date = LegalUi.dateInput(this, f, "Date", d.date);
        Spinner cat = LegalUi.spinner(this, f, "Category", LegalStore.DOC_CATEGORIES, d.category);

        LegalUi.formDialog(this, existing == null ? "Add document" : "Edit document", f, () -> {
            if (!LegalUi.need(this, LegalUi.str(name), "File name")) return false;
            d.name = LegalUi.str(name);
            d.caseName = LegalUi.str(cs);
            d.size = LegalUi.str(size);
            d.date = LegalUi.str(date);
            d.category = (String) cat.getSelectedItem();
            store.saveDoc(d);
            renderList();
            return true;
        }, existing == null ? null : () -> {
            store.deleteDoc(d.id);
            renderList();
        });
    }
}
