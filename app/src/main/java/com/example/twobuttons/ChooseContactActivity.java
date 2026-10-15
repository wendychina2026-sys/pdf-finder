package com.example.twobuttons;

import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** "Choose Contact" screen: searchable list of phone contacts. Returns "name" and "number". */
public class ChooseContactActivity extends BaseActivity {

    private final List<String[]> all = new ArrayList<>();     // {name, number}
    private final List<String[]> shown = new ArrayList<>();
    private BaseAdapter adapter;
    private TextView empty;
    private EditText search;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().hide();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AppTheme.bg(this));

        // header
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(8), dp(10), dp(12), dp(10));
        android.widget.ImageView x = CaseViews.icon(this, R.drawable.ic_close, AppTheme.text(this), 24);
        x.setPadding(dp(8), dp(8), dp(8), dp(8));
        x.setContentDescription("Close");
        x.setOnClickListener(v -> finish());
        h.addView(x, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView t = CaseViews.text(this, "Choose Contact", 18, AppTheme.text(this), true);
        t.setGravity(Gravity.CENTER);
        h.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        h.addView(new View(this), new LinearLayout.LayoutParams(dp(44), dp(44)));   // keeps title centred
        root.addView(h);

        // search box
        LinearLayout sr = new LinearLayout(this);
        sr.setOrientation(LinearLayout.HORIZONTAL);
        sr.setGravity(Gravity.CENTER_VERTICAL);
        sr.setPadding(dp(16), 0, dp(16), 0);
        sr.setBackground(CaseViews.box(this, AppTheme.field(this), 26, AppTheme.fieldStroke(this), 1));
        sr.addView(CaseViews.icon(this, R.drawable.ic_search, AppTheme.sub(this), 22));
        search = new EditText(this);
        search.setHint("Search contacts by name...");
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setTextColor(AppTheme.text(this));
        search.setHintTextColor(AppTheme.sub(this));
        search.setBackground(null);
        search.setPadding(dp(12), dp(14), 0, dp(14));
        sr.addView(search, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.setMargins(dp(16), dp(4), dp(16), dp(8));
        root.addView(sr, slp);

        empty = CaseViews.text(this, "Loading contacts...", 15, AppTheme.sub(this), false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(40), 0, 0);
        root.addView(empty);

        ListView lv = new ListView(this);
        lv.setDivider(new ColorDrawable(AppTheme.cardStroke(this)));
        lv.setDividerHeight(1);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View convert, ViewGroup parent) {
                return row(shown.get(i));
            }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((p, v, pos, id) -> {
            String[] c = shown.get(pos);
            android.content.Intent r = new android.content.Intent();
            r.putExtra("name", c[0]);
            r.putExtra("number", c[1]);
            setResult(RESULT_OK, r);
            finish();
        });
        root.addView(lv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { applyFilter(); }
        });

        loadContacts();
    }

    private int dp(int v) { return CaseViews.dp(this, v); }

    private View row(String[] c) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(20), dp(16), dp(16), dp(16));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(CaseViews.text(this, c[0], 15, AppTheme.text(this), false));
        TextView num = CaseViews.text(this, c[1], 15, AppTheme.sub(this), false);
        num.setPadding(0, dp(4), 0, 0);
        col.addView(num);
        r.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(CaseViews.icon(this, R.drawable.ic_chevron_right, AppTheme.sub(this), 24));
        return r;
    }

    private void loadContacts() {
        new Thread(() -> {
            List<String[]> found = new ArrayList<>();
            boolean denied = false;
            try (Cursor cur = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER},
                    null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC")) {
                Set<String> seen = new HashSet<>();
                if (cur != null) {
                    while (cur.moveToNext()) {
                        String name = cur.getString(0);
                        String number = cur.getString(1);
                        if (name == null || number == null || number.trim().isEmpty()) continue;
                        String key = name + "|" + CountryCodes.digits(number);
                        if (seen.add(key)) found.add(new String[]{name.trim(), number.trim()});
                    }
                }
            } catch (SecurityException e) {
                denied = true;
            }
            final boolean noAccess = denied;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (noAccess) {
                    Toast.makeText(this, "Contacts permission is needed", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                all.clear();
                all.addAll(found);
                applyFilter();
            });
        }).start();
    }

    private void applyFilter() {
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        String qd = CountryCodes.digits(q);
        shown.clear();
        for (String[] c : all) {
            if (q.isEmpty() || c[0].toLowerCase(Locale.ROOT).contains(q)
                    || (!qd.isEmpty() && CountryCodes.digits(c[1]).contains(qd))) {
                shown.add(c);
            }
        }
        adapter.notifyDataSetChanged();
        empty.setText("No contacts found");
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
