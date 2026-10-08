package com.example.twobuttons;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/** All hearings for a case: details, payment bar, timeline. Add / edit / delete hearings. */
public class HearingsActivity extends BaseActivity {

    private static final int TEAL = 0xFF1F5566;
    private static final int M_EDIT = 1, M_DONE = 2, M_DELETE = 3, M_SWITCH = 4;

    private DiaryStore store;
    private LinearLayout box;
    private String curId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Hearings");
        store = new DiaryStore(this);
        curId = getIntent().getStringExtra("id");

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(AppTheme.isDark(this) ? AppTheme.bg(this) : 0xFFF0F5FA);

        ScrollView sv = new ScrollView(this);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = LegalUi.dp(this, 16);
        box.setPadding(p, p, p, p);
        sv.addView(box);
        page.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView add = CaseViews.text(this, "+  Add Hearing", 17, 0xFFFFFFFF, true);
        add.setGravity(Gravity.CENTER);
        add.setBackground(CaseViews.box(this, TEAL, 30, 0, 0));
        add.setPadding(LegalUi.dp(this, 26), LegalUi.dp(this, 16), LegalUi.dp(this, 26), LegalUi.dp(this, 16));
        add.setOnClickListener(v -> {
            DiaryStore.HCase c = current();
            if (c == null) { Toast.makeText(this, "No case", Toast.LENGTH_SHORT).show(); return; }
            editHearing(c, null);
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        alp.gravity = Gravity.END;
        alp.setMargins(p, LegalUi.dp(this, 4), p, p);
        page.addView(add, alp);
        setContentView(page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private DiaryStore.HCase current() {
        DiaryStore.HCase c = store.caseById(curId);
        if (c == null) {
            List<DiaryStore.HCase> all = store.cases();
            if (!all.isEmpty()) { c = all.get(0); curId = c.id; }
        }
        return c;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        m.add(0, M_EDIT, 0, "Edit case details & bill");
        m.add(0, M_DONE, 1, "Toggle case completed");
        m.add(0, M_DELETE, 2, "Delete case");
        m.add(0, M_SWITCH, 3, "Switch case");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem it) {
        DiaryStore.HCase c = current();
        switch (it.getItemId()) {
            case M_EDIT:
                if (c != null) editDetails(c);
                return true;
            case M_DONE:
                if (c != null) { c.completed = !c.completed; store.saveCase(c); render(); }
                return true;
            case M_DELETE:
                if (c != null) LegalUi.confirm(this, "Delete " + c.title() + "?",
                        "Case and its hearings are removed.", () -> {
                            store.deleteCase(c.id);
                            curId = null;
                            render();
                        });
                return true;
            case M_SWITCH:
                List<DiaryStore.HCase> all = store.cases();
                if (all.isEmpty()) return true;
                String[] names = new String[all.size()];
                for (int i = 0; i < names.length; i++) names[i] = all.get(i).title();
                new AlertDialog.Builder(this).setTitle("Cases").setItems(names, (d, w) -> {
                    curId = all.get(w).id;
                    render();
                }).show();
                return true;
            default:
                return super.onOptionsItemSelected(it);
        }
    }

    private String n(long v) { return String.format(Locale.US, "%,d", v); }

    private void render() {
        box.removeAllViews();
        DiaryStore.HCase c = current();
        if (c == null) {
            box.addView(CaseViews.emptyNote(this, "No cases"));
            return;
        }
        int txt = AppTheme.text(this), sub = AppTheme.sub(this);

        box.addView(CaseViews.text(this, "All hearings for", 13, sub, false));
        box.addView(CaseViews.text(this, c.title(), 20, txt, true));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(0, LegalUi.dp(this, 12), 0, LegalUi.dp(this, 14));
        info.addView(line("Representing: ", c.representing));
        info.addView(line("In court of: ", c.court));
        info.addView(line("Case No: ", c.number));
        info.addView(line("Type: ", c.subType.isEmpty() ? c.type : c.subType));
        box.addView(info);

        // payment card
        LinearLayout pay = new LinearLayout(this);
        pay.setOrientation(LinearLayout.VERTICAL);
        pay.setPadding(LegalUi.dp(this, 20), LegalUi.dp(this, 16), LegalUi.dp(this, 20), LegalUi.dp(this, 16));
        pay.setBackground(CaseViews.box(this, TEAL, 14, 0, 0));
        LinearLayout pend = new LinearLayout(this);
        pend.setOrientation(LinearLayout.HORIZONTAL);
        pend.addView(CaseViews.text(this, "Payment Pending: ", 15, 0xFFFFFFFF, false));
        pend.addView(CaseViews.text(this, n(c.pending()), 15, 0xFFFFD54F, true));
        pay.addView(pend);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        float paid = c.billTotal <= 0 ? 0f : Math.min(1f, (float) c.billPaid / c.billTotal);
        View g = new View(this);
        g.setBackground(CaseViews.box(this, 0xFF4CD27A, 4, 0, 0));
        View w = new View(this);
        w.setBackground(CaseViews.box(this, 0xFFFFFFFF, 4, 0, 0));
        bar.addView(g, new LinearLayout.LayoutParams(0, LegalUi.dp(this, 8), Math.max(paid, 0.0001f)));
        bar.addView(w, new LinearLayout.LayoutParams(0, LegalUi.dp(this, 8), Math.max(1f - paid, 0.0001f)));
        pay.addView(bar, CaseViews.lp(this, 12, 8));

        LinearLayout nums = new LinearLayout(this);
        nums.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout left = new LinearLayout(this);
        TextView pd = CaseViews.text(this, n(c.billPaid), 15, 0xFF4CD27A, true);
        pd.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC);
        left.addView(pd);
        TextView tt = CaseViews.text(this, " / " + n(c.billTotal), 15, 0xFFFFFFFF, true);
        tt.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC);
        left.addView(tt);
        nums.addView(left, LegalUi.weight());
        nums.addView(CaseViews.text(this, "Total Bill: ", 14, 0xFFFFFFFF, false));
        nums.addView(CaseViews.text(this, n(c.billTotal), 14, 0xFFFFFFFF, true));
        pay.addView(nums);
        box.addView(pay);

        // timeline
        if (c.completed) box.addView(node(true, R.drawable.ic_check, "Case completed", null, null), CaseViews.lp(this, 18, 0));
        for (int i = 0; i < c.hearings.size(); i++) {
            DiaryStore.Hearing h = c.hearings.get(i);
            box.addView(node(false, 0, h.date, h.note, h), CaseViews.lp(this, 0, 0));
        }
        box.addView(node(true, R.drawable.ic_edit, "", null, null), CaseViews.lp(this, 0, 60));
    }

    private LinearLayout line(String k, String v) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, LegalUi.dp(this, 2), 0, LegalUi.dp(this, 2));
        r.addView(CaseViews.text(this, k, 15, AppTheme.sub(this), false));
        r.addView(CaseViews.text(this, v.isEmpty() ? "-" : v, 16, AppTheme.text(this), false));
        return r;
    }

    /** One timeline row. big=true -> icon node (top "Case completed" / bottom note). */
    private LinearLayout node(boolean big, int iconRes, String title, String note, DiaryStore.Hearing h) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        FrameLayout rail = new FrameLayout(this);
        View line = new View(this);
        line.setBackgroundColor(TEAL);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(LegalUi.dp(this, 3), FrameLayout.LayoutParams.MATCH_PARENT);
        llp.gravity = Gravity.CENTER_HORIZONTAL;
        rail.addView(line, llp);
        int d = big ? 34 : 24;
        View dot;
        if (big) {
            LinearLayout c = new LinearLayout(this);
            c.setGravity(Gravity.CENTER);
            c.setBackground(CaseViews.box(this, TEAL, 17, 0, 0));
            c.addView(CaseViews.icon(this, iconRes, 0xFFFFFFFF, 20));
            dot = c;
        } else {
            dot = new View(this);
            dot.setBackground(CaseViews.box(this, TEAL, 12, 0, 0));
        }
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(LegalUi.dp(this, d), LegalUi.dp(this, d));
        dlp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        rail.addView(dot, dlp);
        row.addView(rail, new LinearLayout.LayoutParams(LegalUi.dp(this, 34), LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(LegalUi.dp(this, 10), big ? LegalUi.dp(this, 4) : 0, 0, LegalUi.dp(this, 36));
        if (h == null) {
            col.addView(CaseViews.text(this, title, 16, AppTheme.sub(this), false));
        } else {
            col.addView(CaseViews.text(this, title, 16, AppTheme.text(this), true));
            TextView nt = CaseViews.text(this, note, 14, AppTheme.sub(this), false);
            nt.setPadding(0, LegalUi.dp(this, 10), 0, 0);
            col.addView(nt);
            row.setOnClickListener(v -> editHearing(current(), h));
        }
        row.addView(col, LegalUi.weight());
        return row;
    }

    private void editDetails(DiaryStore.HCase c) {
        LinearLayout f = LegalUi.form(this);
        EditText first = LegalUi.text(this, f, "First party", c.first);
        EditText second = LegalUi.text(this, f, "Second party", c.second);
        EditText rep = LegalUi.text(this, f, "Representing", c.representing);
        EditText court = LegalUi.text(this, f, "In court of", c.court);
        EditText no = LegalUi.text(this, f, "Case No", c.number);
        EditText type = LegalUi.text(this, f, "Type", c.subType);
        EditText total = LegalUi.input(this, f, "Total bill", String.valueOf(c.billTotal), InputType.TYPE_CLASS_NUMBER);
        EditText paid = LegalUi.input(this, f, "Paid", String.valueOf(c.billPaid), InputType.TYPE_CLASS_NUMBER);
        LegalUi.formDialog(this, "Edit case details", f, () -> {
            if (!LegalUi.need(this, LegalUi.str(first), "First party")) return false;
            c.first = LegalUi.str(first);
            c.second = LegalUi.str(second);
            c.representing = LegalUi.str(rep);
            c.court = LegalUi.str(court);
            c.number = LegalUi.str(no);
            c.subType = LegalUi.str(type);
            c.billTotal = LegalUi.num(total);
            c.billPaid = LegalUi.num(paid);
            store.saveCase(c);
            render();
            return true;
        }, null);
    }

    private void editHearing(DiaryStore.HCase c, DiaryStore.Hearing existing) {
        DiaryStore.Hearing h = existing != null ? existing : new DiaryStore.Hearing();
        LinearLayout f = LegalUi.form(this);
        EditText date = LegalUi.dateInput(this, f, "Date", h.date);
        EditText note = LegalUi.multiline(this, f, "What happened", h.note);
        note.setMinLines(3);
        LegalUi.formDialog(this, existing == null ? "Add hearing" : "Edit hearing", f, () -> {
            if (!LegalUi.need(this, LegalUi.str(date), "Date")) return false;
            h.date = LegalUi.str(date);
            h.note = note.getText().toString().trim();
            if (existing == null) c.hearings.add(0, h);
            store.saveCase(c);
            render();
            return true;
        }, existing == null ? null : () -> {
            c.hearings.remove(existing);
            store.saveCase(c);
            render();
        });
    }
}
