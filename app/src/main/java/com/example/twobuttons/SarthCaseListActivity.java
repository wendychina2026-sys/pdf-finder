package com.example.twobuttons;

import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Case List page: search, calendar filter (adjourn date), add / edit / delete. */
public class SarthCaseListActivity extends BaseActivity {

    private DiaryStore store;
    private LinearLayout list;
    private EditText search;
    private String dateFilter = "";
    private ImageView cal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Case List");
        store = new DiaryStore(this);
        int p = LegalUi.dp(this, 16);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(AppTheme.bg(this));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xFF0D47A1);
        bar.setPadding(LegalUi.dp(this, 12), LegalUi.dp(this, 10), LegalUi.dp(this, 12), LegalUi.dp(this, 12));
        search = new EditText(this);
        search.setHint("Search");
        search.setSingleLine(true);
        search.setTextColor(0xFF000000);
        search.setHintTextColor(0xFF757575);
        search.setPadding(LegalUi.dp(this, 14), LegalUi.dp(this, 10), LegalUi.dp(this, 14), LegalUi.dp(this, 10));
        search.setBackground(CaseViews.box(this, 0xFFFFFFFF, 10, 0xFF9E9E9E, 1));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { render(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        bar.addView(search, LegalUi.weight());
        cal = CaseViews.icon(this, R.drawable.ic_calendar, 0xFFFFFFFF, 34);
        cal.setOnClickListener(v -> pickDate());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(LegalUi.dp(this, 34), LegalUi.dp(this, 34));
        clp.leftMargin = LegalUi.dp(this, 12);
        bar.addView(cal, clp);
        page.addView(bar);

        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(p, p, p, p);
        sv.addView(list);
        page.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        m.add(0, 1, 0, "Add case");
        m.add(0, 2, 1, "Clear date filter");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem it) {
        if (it.getItemId() == 1) { edit(null); return true; }
        if (it.getItemId() == 2) { dateFilter = ""; render(); return true; }
        return super.onOptionsItemSelected(it);
    }

    private void pickDate() {
        Calendar c = Calendar.getInstance();
        Date d = DiaryStore.parse(dateFilter);
        if (d != null) c.setTime(d);
        new DatePickerDialog(this, (v, y, m, day) -> {
            Calendar x = Calendar.getInstance();
            x.set(y, m, day);
            dateFilter = DiaryStore.fmt(x.getTime());
            render();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void render() {
        if (list == null) return;
        list.removeAllViews();
        String q = search.getText().toString().trim().toLowerCase(Locale.US);
        if (!dateFilter.isEmpty()) {
            list.addView(CaseViews.text(this, "Adjourn date: " + dateFilter + "  (menu > Clear date filter)",
                    13, AppTheme.accent(this), true), CaseViews.lp(this, 0, 8));
        }
        int n = 0;
        for (DiaryStore.LCase c : store.listCases()) {
            String all = (c.title + " " + c.court + " " + c.caseType + " " + c.caseNo + " " + c.contact
                    + " " + c.phone + " " + c.onBehalf + " " + c.respondent).toLowerCase(Locale.US);
            if (!q.isEmpty() && !all.contains(q)) continue;
            if (!dateFilter.isEmpty() && !dateFilter.equals(c.adjourn)) continue;
            list.addView(card(c), CaseViews.lp(this, 0, 14));
            n++;
        }
        if (n == 0) list.addView(CaseViews.emptyNote(this, "No cases"));
    }

    private LinearLayout card(DiaryStore.LCase c) {
        LinearLayout card = LegalUi.card(this);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(CaseViews.text(this, c.title, 20, AppTheme.text(this), true), LegalUi.weight());
        top.addView(CaseViews.text(this, ">", 22, AppTheme.text(this), true));
        card.addView(top);

        LinearLayout cols = new LinearLayout(this);
        cols.setOrientation(LinearLayout.HORIZONTAL);
        cols.setPadding(0, LegalUi.dp(this, 6), 0, LegalUi.dp(this, 10));
        cols.addView(col("Court", c.court), LegalUi.weight());
        cols.addView(col("Case Type", c.caseType), LegalUi.weight());
        LinearLayout last = col("Case#", c.caseNo);
        last.setGravity(Gravity.END);
        cols.addView(last, LegalUi.weight());
        card.addView(cols);

        LinearLayout who = new LinearLayout(this);
        who.setOrientation(LinearLayout.HORIZONTAL);
        who.setGravity(Gravity.CENTER_VERTICAL);
        who.addView(CaseViews.icon(this, R.drawable.ic_person, AppTheme.sub(this), 26));
        TextView nm = CaseViews.text(this, c.contact, 16, AppTheme.text(this), true);
        nm.setPadding(LegalUi.dp(this, 10), 0, LegalUi.dp(this, 10), 0);
        who.addView(nm);
        TextView ph = CaseViews.text(this, c.phone, 16, 0xFF1E88E5, false);
        ph.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.phone)));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "No dialer", Toast.LENGTH_SHORT).show();
            }
        });
        who.addView(ph);
        card.addView(who);

        card.addView(kv("On Behalf Of", c.onBehalf), CaseViews.lp(this, 10, 0));
        card.addView(kv("Respondent Name", c.respondent), CaseViews.lp(this, 6, 0));
        card.addView(kv("Previous Date", c.prev.isEmpty() ? "-" : c.prev), CaseViews.lp(this, 6, 0));
        card.addView(kv("Adjourn Date", c.adjourn.isEmpty() ? "-" : c.adjourn), CaseViews.lp(this, 6, 0));
        card.addView(kv("Steps", String.valueOf(c.steps)), CaseViews.lp(this, 6, 0));
        card.setOnClickListener(v -> edit(c));
        return card;
    }

    private LinearLayout col(String label, String value) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.addView(CaseViews.text(this, label, 14, AppTheme.text(this), true));
        l.addView(CaseViews.text(this, value, 16, AppTheme.text(this), false));
        return l;
    }

    private LinearLayout kv(String k, String v) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(CaseViews.text(this, k, 16, AppTheme.text(this), true));
        TextView t = CaseViews.text(this, v, 16, AppTheme.sub(this), false);
        t.setPadding(LegalUi.dp(this, 10), 0, 0, 0);
        row.addView(t, LegalUi.weight());
        return row;
    }

    private void edit(DiaryStore.LCase existing) {
        DiaryStore.LCase c = existing != null ? existing : new DiaryStore.LCase();
        LinearLayout f = LegalUi.form(this);
        EditText title = LegalUi.text(this, f, "Title", c.title);
        EditText court = LegalUi.text(this, f, "Court", c.court);
        EditText type = LegalUi.text(this, f, "Case type", c.caseType);
        EditText no = LegalUi.text(this, f, "Case #", c.caseNo);
        EditText contact = LegalUi.text(this, f, "Contact name", c.contact);
        EditText phone = LegalUi.input(this, f, "Phone", c.phone, InputType.TYPE_CLASS_PHONE);
        EditText onb = LegalUi.text(this, f, "On behalf of", c.onBehalf);
        EditText resp = LegalUi.text(this, f, "Respondent name", c.respondent);
        EditText prev = LegalUi.dateInput(this, f, "Previous date (dd/MM/yyyy)", c.prev);
        EditText adj = LegalUi.dateInput(this, f, "Adjourn date (dd/MM/yyyy)", c.adjourn);
        EditText steps = LegalUi.input(this, f, "Steps", String.valueOf(c.steps), InputType.TYPE_CLASS_NUMBER);
        // date pickers here write dd/MM/yyyy
        prev.setOnClickListener(v -> pick(prev));
        adj.setOnClickListener(v -> pick(adj));

        LegalUi.formDialog(this, existing == null ? "Add case" : "Edit case", f, () -> {
            if (!LegalUi.need(this, LegalUi.str(title), "Title")) return false;
            c.title = LegalUi.str(title);
            c.court = LegalUi.str(court);
            c.caseType = LegalUi.str(type);
            c.caseNo = LegalUi.str(no);
            c.contact = LegalUi.str(contact);
            c.phone = LegalUi.str(phone);
            c.onBehalf = LegalUi.str(onb);
            c.respondent = LegalUi.str(resp);
            c.prev = LegalUi.str(prev);
            c.adjourn = LegalUi.str(adj);
            c.steps = (int) LegalUi.num(steps);
            store.saveListCase(c);
            render();
            return true;
        }, existing == null ? null : () -> {
            store.deleteListCase(c.id);
            render();
        });
    }

    private void pick(EditText target) {
        Calendar c = Calendar.getInstance();
        Date d = DiaryStore.parse(target.getText().toString());
        if (d != null) c.setTime(d);
        new DatePickerDialog(this, (v, y, m, day) -> {
            Calendar x = Calendar.getInstance();
            x.set(y, m, day);
            target.setText(DiaryStore.fmt(x.getTime()));
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }
}
