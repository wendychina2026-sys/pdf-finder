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

    private static final String[] FILTERS = filters();

    private static String[] filters() {
        String[] cats = LegalStore.allDocCategories();
        String[] out = new String[cats.length + 1];
        out[0] = "All";
        System.arraycopy(cats, 0, out, 1, cats.length);
        return out;
    }

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

        search = new RoundEdit(this);
        search.setHint("Search file, case or client");
        search.setSingleLine(true);
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

    private static final int REQ_REPLACE = 61, REQ_SAVE = 62;
    private LegalStore.Doc replacing;

    private LinearLayout row(LegalStore.Doc d) {
        final android.net.Uri uri = d.uri.isEmpty() ? null : android.net.Uri.parse(d.uri);
        DocFileCard.Info info = new DocFileCard.Info();
        info.name = d.name;
        info.category = d.category;
        info.matter = d.caseName;
        info.stage = d.stage;
        info.size = d.size;
        info.date = d.date;
        info.uri = uri;
        DocFileCard.Actions act = new DocFileCard.Actions();
        act.open = () -> DocFileCard.open(this, uri, d.name);
        act.save = () -> DocFileCard.startSave(this, REQ_SAVE, uri, d.name);
        act.share = () -> DocFileCard.share(this, uri, d.name);
        act.replace = () -> LegalUi.confirm(this, "Replace " + d.name + "?",
                "The stored file will be swapped for the one you pick.", () -> {
                    replacing = d;
                    android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    i.putExtra(android.content.Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf",
                            "application/msword",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "image/*"});
                    i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    try {
                        startActivityForResult(i, REQ_REPLACE);
                    } catch (Exception e) {
                        android.widget.Toast.makeText(this, "No file picker available", android.widget.Toast.LENGTH_LONG).show();
                    }
                });
        act.delete = () -> LegalUi.confirm(this, "Delete " + d.name + "?", "This cannot be undone.", () -> {
            store.deleteDoc(d.id);
            renderList();
        });
        LinearLayout card = DocFileCard.build(this, info, act);
        card.setOnLongClickListener(v -> {      // long press = edit caption / category / matter
            editDoc(d);
            return true;
        });
        return card;
    }

    @Override
    protected void onActivityResult(int req, int res, android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_SAVE) {
            DocFileCard.finishSave(this, res, data);
            return;
        }
        if (req == REQ_REPLACE && res == RESULT_OK && data != null && data.getData() != null && replacing != null) {
            android.net.Uri u = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {
                // not every provider offers a persistable grant
            }
            String nm = null;
            long sz = 0;
            try (android.database.Cursor c = getContentResolver().query(u, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int ni = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    int si = c.getColumnIndex(android.provider.OpenableColumns.SIZE);
                    if (ni >= 0) nm = c.getString(ni);
                    if (si >= 0 && !c.isNull(si)) sz = c.getLong(si);
                }
            } catch (Exception ignored) {
                // keep old name / size
            }
            String ext = nm != null && nm.lastIndexOf('.') > 0 ? nm.substring(nm.lastIndexOf('.')) : "";
            replacing.name = DocFileCard.stem(replacing.name) + ext;
            replacing.uri = u.toString();
            if (sz > 0) replacing.size = sz < 1024 * 1024 ? String.format(Locale.US, "%.1f KB", sz / 1024.0)
                    : String.format(Locale.US, "%.1f MB", sz / (1024.0 * 1024.0));
            replacing.date = new SimpleDateFormat("dd MMM yyyy", Locale.US).format(new Date());
            store.saveDoc(replacing);
            replacing = null;
            renderList();
        }
    }

    private void editDoc(LegalStore.Doc existing) {
        LegalStore.Doc d = existing != null ? existing : new LegalStore.Doc();
        if (existing == null) d.date = new SimpleDateFormat("dd MMM yyyy", Locale.US).format(new Date());
        LinearLayout f = LegalUi.form(this);
        EditText name = LegalUi.text(this, f, "File name (with extension)", d.name);
        EditText cs = LegalUi.text(this, f, "Case / client", d.caseName);
        EditText size = LegalUi.text(this, f, "Size (e.g. 1.2 MB)", d.size);
        EditText date = LegalUi.dateInput(this, f, "Date", d.date);
        Spinner cat = LegalUi.spinner(this, f, "Category", LegalStore.allDocCategories(), d.category);

        LegalUi.formDialog(this, existing == null ? "Add document" : "Edit document", f, () -> {
            if (!LegalUi.need(this, name, "File name")) return false;
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
