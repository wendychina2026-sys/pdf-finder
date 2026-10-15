package com.example.twobuttons;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/** Home: Today / Weekly hearings with expandable cards, Notes, bottom bar. */
public class CasesHomeActivity extends BaseActivity {

    private static final int BLACK = 0xFF1C1C1C;
    private static final int GOLD = 0xFFC49A3C;
    private static final int RED = 0xFFD32F2F;

    private DiaryStore store;
    private LinearLayout content, header;
    private boolean weekly = false;
    private boolean filedOnly = true;
    private String expandedId = "";
    private boolean expandedSet = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Home");
        store = new DiaryStore(this);
        int bg = AppTheme.isDark(this) ? AppTheme.bg(this) : 0xFFF8F8FB;

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(bg);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = LegalUi.dp(this, 16);
        root.setPadding(p, p, p, p);

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, CaseViews.lp(this, 0, 8));

        // filter popup
        TextView filter = CaseViews.text(this, "Filed cases  \u25BE", 15, AppTheme.text(this), true);
        filter.setPadding(LegalUi.dp(this, 16), LegalUi.dp(this, 10), LegalUi.dp(this, 16), LegalUi.dp(this, 10));
        filter.setBackground(CaseViews.box(this, AppTheme.field(this), 22, AppTheme.fieldStroke(this), 1));
        filter.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(this, v);
            pm.getMenu().add(0, 1, 0, "Filed cases");
            pm.getMenu().add(0, 2, 1, "Completed cases");
            pm.setOnMenuItemClickListener(mi -> {
                filedOnly = mi.getItemId() == 1;
                filter.setText((filedOnly ? "Filed cases" : "Completed cases") + "  \u25BE");
                render();
                return true;
            });
            pm.show();
        });
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        flp.gravity = Gravity.END;
        root.addView(filter, flp);

        // Today | Weekly toggle
        LinearLayout tog = new LinearLayout(this);
        tog.setOrientation(LinearLayout.HORIZONTAL);
        tog.setPadding(LegalUi.dp(this, 8), LegalUi.dp(this, 8), LegalUi.dp(this, 8), LegalUi.dp(this, 8));
        tog.setBackground(CaseViews.box(this, AppTheme.field(this), 16, AppTheme.fieldStroke(this), 1));
        TextView tToday = toggle("Today", true);
        TextView tWeek = toggle("Weekly", false);
        tToday.setOnClickListener(v -> { weekly = false; styleToggle(tToday, tWeek); render(); });
        tWeek.setOnClickListener(v -> { weekly = true; styleToggle(tToday, tWeek); render(); });
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, LegalUi.dp(this, 52), 1f);
        tl.rightMargin = LegalUi.dp(this, 8);
        tog.addView(tToday, tl);
        tog.addView(tWeek, new LinearLayout.LayoutParams(0, LegalUi.dp(this, 52), 1f));
        root.addView(tog, CaseViews.lp(this, 16, 12));
        styleToggle(tToday, tWeek);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content);

        sv.addView(root);
        page.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(bottomBar());
        setContentView(page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private TextView toggle(String s, boolean on) {
        TextView t = CaseViews.text(this, s, 17, 0xFFFFFFFF, true);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private void styleToggle(TextView today, TextView week) {
        TextView on = weekly ? week : today, off = weekly ? today : week;
        on.setTextColor(0xFFFFFFFF);
        on.setBackground(CaseViews.box(this, BLACK, 14, 0, 0));
        off.setTextColor(0xFF5A6075);
        off.setBackground(CaseViews.box(this, AppTheme.isDark(this) ? 0xFF2A2C3A : 0xFFE8E8E8, 14, 0, 0));
    }

    // ---------- bottom bar ----------

    private LinearLayout bottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(BLACK);
        bar.setPadding(0, LegalUi.dp(this, 8), 0, LegalUi.dp(this, 10));
        bar.addView(navItem(R.drawable.ic_home, "Home", true, v -> {}), LegalUi.weight());
        bar.addView(navItem(R.drawable.ic_calendar, "Calendar", false, v -> pickCalendar()), LegalUi.weight());
        TextView plus = CaseViews.text(this, "+", 34, 0xFFFFFFFF, false);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(CaseViews.box(this, BLACK, 32, 0xFF555555, 3));
        plus.setOnClickListener(v -> startActivity(new Intent(this, AddCaseActivity.class)));
        bar.addView(plus, new LinearLayout.LayoutParams(LegalUi.dp(this, 64), LegalUi.dp(this, 64)));
        bar.addView(navItem(R.drawable.ic_pdf, "All Cases", false,
                v -> startActivity(new Intent(this, SarthCaseListActivity.class))), LegalUi.weight());
        bar.addView(navItem(R.drawable.ic_person, "Profile", false,
                v -> startActivity(new Intent(this, SettingsActivity.class))), LegalUi.weight());
        return bar;
    }

    private LinearLayout navItem(int icon, String label, boolean on, View.OnClickListener click) {
        int col = on ? 0xFFF3E3C0 : 0xFFB8BAC8;
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.addView(CaseViews.icon(this, icon, col, 28));
        TextView t = CaseViews.text(this, label, 13, col, on);
        t.setPadding(0, LegalUi.dp(this, 3), 0, 0);
        l.addView(t);
        l.setOnClickListener(click);
        return l;
    }

    private void pickCalendar() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (v, y, m, d) -> {
            Calendar x = Calendar.getInstance();
            x.set(y, m, d);
            String ds = DiaryStore.fmt(x.getTime());
            List<String> names = new ArrayList<>();
            for (DiaryStore.HCase cs : store.cases()) if (ds.equals(cs.next)) names.add(cs.title());
            new AlertDialog.Builder(this)
                    .setTitle("Hearings on " + ds)
                    .setMessage(names.isEmpty() ? "Nothing on this date" : joinLines(names))
                    .setPositiveButton("OK", null)
                    .show();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private static String joinLines(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) { if (sb.length() > 0) sb.append("\n"); sb.append(s); }
        return sb.toString();
    }

    // ---------- data ----------

    private List<DiaryStore.HCase> visible() {
        Date today = DiaryStore.today();
        Date end = DiaryStore.weekEnd();
        List<DiaryStore.HCase> out = new ArrayList<>();
        for (DiaryStore.HCase c : store.cases()) {
            if (!filedOnly) {
                if (c.completed) out.add(c);
                continue;
            }
            if (c.completed) continue;
            Date n = DiaryStore.parse(c.next);
            if (n == null) continue;
            if (weekly ? !n.after(end) : n.equals(today)) out.add(c);
        }
        Collections.sort(out, (a, b) -> {
            Date x = DiaryStore.parse(a.next), y = DiaryStore.parse(b.next);
            if (x == null || y == null) return 0;
            return x.compareTo(y);
        });
        return out;
    }

    private boolean overdue(DiaryStore.HCase c) {
        Date n = DiaryStore.parse(c.next);
        return n != null && n.before(DiaryStore.today());
    }

    // ---------- render ----------

    private void render() {
        if (content == null) return;
        header.removeAllViews();
        TextView title = CaseViews.text(this, weekly ? "This week Cases" : "Today", 22, AppTheme.text(this), true);
        header.addView(title, LegalUi.weight());
        if (weekly) {
            ImageView share = CaseViews.icon(this, R.drawable.ic_share, AppTheme.text(this), 26);
            share.setOnClickListener(v -> shareWeek());
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(LegalUi.dp(this, 26), LegalUi.dp(this, 26));
            slp.rightMargin = LegalUi.dp(this, 16);
            header.addView(share, slp);
        }
        TextView notes = CaseViews.text(this, "\u270E  Notes", 15, 0xFFFFFFFF, true);
        notes.setPadding(LegalUi.dp(this, 16), LegalUi.dp(this, 10), LegalUi.dp(this, 16), LegalUi.dp(this, 10));
        notes.setBackground(CaseViews.box(this, GOLD, 22, 0, 0));
        notes.setOnClickListener(v -> notesDialog());
        header.addView(notes);

        content.removeAllViews();
        List<DiaryStore.HCase> list = visible();
        if (list.isEmpty()) {
            content.addView(emptyCard(), CaseViews.lp(this, 12, 0));
            return;
        }
        TextView sec = CaseViews.text(this, !filedOnly ? "COMPLETED CASES" : (weekly ? "HEARINGS THIS WEEK" : "HEARINGS TODAY"),
                16, 0xFF5A6075, true);
        sec.setLetterSpacing(0.08f);
        sec.setPadding(0, LegalUi.dp(this, 14), 0, LegalUi.dp(this, 14));
        content.addView(sec);
        if (!expandedSet) { expandedId = list.get(0).id; expandedSet = true; }
        for (DiaryStore.HCase c : list) {
            content.addView(c.id.equals(expandedId) ? expanded(c) : collapsed(c), CaseViews.lp(this, 0, 12));
        }
    }

    private LinearLayout emptyCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(LegalUi.dp(this, 24), LegalUi.dp(this, 36), LegalUi.dp(this, 24), LegalUi.dp(this, 30));
        card.setBackground(CaseViews.box(this, AppTheme.isDark(this) ? AppTheme.card(this) : 0xFFFFFFFF, 20,
                AppTheme.cardStroke(this), 1));
        card.setElevation(LegalUi.dp(this, 6));
        LinearLayout ic = new LinearLayout(this);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(CaseViews.box(this, AppTheme.isDark(this) ? 0xFF2A2C44 : 0xFFEEF3FF, 58, 0, 0));
        ic.addView(CaseViews.icon(this, R.drawable.ic_calendar, GOLD, 40));
        card.addView(ic, new LinearLayout.LayoutParams(LegalUi.dp(this, 116), LegalUi.dp(this, 116)));
        TextView h = CaseViews.text(this, filedOnly ? (weekly ? "Nothing this week" : "Nothing for today") : "No completed cases",
                23, AppTheme.text(this), true);
        h.setPadding(0, LegalUi.dp(this, 20), 0, LegalUi.dp(this, 8));
        card.addView(h);
        TextView s = CaseViews.text(this, weekly ? "Cases with a hearing this week will appear here."
                : "Cases with a hearing today will appear here.", 17, 0xFF5A6075, false);
        s.setGravity(Gravity.CENTER);
        card.addView(s);
        TextView add = CaseViews.text(this, "+  Add Case", 20, 0xFFFFFFFF, true);
        add.setGravity(Gravity.CENTER);
        add.setBackground(CaseViews.box(this, BLACK, 16, 0, 0));
        add.setPadding(LegalUi.dp(this, 36), LegalUi.dp(this, 18), LegalUi.dp(this, 36), LegalUi.dp(this, 18));
        add.setOnClickListener(v -> startActivity(new Intent(this, AddCaseActivity.class)));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        alp.topMargin = LegalUi.dp(this, 26);
        card.addView(add, alp);
        TextView all = CaseViews.text(this, "View all cases  >", 18, AppTheme.text(this), false);
        all.setPadding(0, LegalUi.dp(this, 22), 0, 0);
        all.setOnClickListener(v -> startActivity(new Intent(this, SarthCaseListActivity.class)));
        card.addView(all);
        return card;
    }

    private int cardFill(DiaryStore.HCase c) {
        if (overdue(c)) return AppTheme.isDark(this) ? 0xFF3A2824 : 0xFFFDEEE8;
        return AppTheme.isDark(this) ? AppTheme.card(this) : 0xFFFFFFFF;
    }

    private LinearLayout collapsed(DiaryStore.HCase c) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(LegalUi.dp(this, 20), LegalUi.dp(this, 16), LegalUi.dp(this, 20), LegalUi.dp(this, 14));
        card.setBackground(CaseViews.box(this, cardFill(c), 16, 0, 0));
        card.setElevation(LegalUi.dp(this, 4));
        card.addView(dateCol("PREVIOUS", c.prev, Gravity.START), LegalUi.weight());
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView t = CaseViews.text(this, c.title(), 18, AppTheme.text(this), true);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setMaxEms(7);
        mid.addView(t);
        mid.addView(CaseViews.text(this, "\u2304", 22, 0xFF5A6075, false));
        card.addView(mid, LegalUi.weight());
        card.addView(dateCol("NEXT", c.next, Gravity.END), LegalUi.weight());
        card.setOnClickListener(v -> { expandedId = c.id; render(); });
        return card;
    }

    private LinearLayout dateCol(String label, String date, int grav) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(grav);
        l.addView(CaseViews.text(this, label, 13, 0xFF5A6075, false));
        TextView d = CaseViews.text(this, date.isEmpty() ? "-" : date, 17, AppTheme.text(this), true);
        d.setPadding(0, LegalUi.dp(this, 6), 0, 0);
        l.addView(d);
        return l;
    }

    private LinearLayout expanded(DiaryStore.HCase c) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(LegalUi.dp(this, 22), LegalUi.dp(this, 14), LegalUi.dp(this, 22), LegalUi.dp(this, 18));
        card.setBackground(CaseViews.box(this, cardFill(c), 16, 0, 0));
        card.setElevation(LegalUi.dp(this, 6));
        TextView up = CaseViews.text(this, "\u2303", 22, 0xFF5A6075, false);
        up.setGravity(Gravity.CENTER);
        up.setOnClickListener(v -> { expandedId = ""; render(); });
        card.addView(up);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(CaseViews.text(this, c.title(), 22, AppTheme.text(this), true), LegalUi.weight());
        TextView det = CaseViews.text(this, "Details  >", 17, 0xFF5A6075, false);
        det.setOnClickListener(v -> {
            Intent i = new Intent(this, HearingsActivity.class);
            i.putExtra("id", c.id);
            startActivity(i);
        });
        top.addView(det);
        card.addView(top, CaseViews.lp(this, 8, 8));

        String tp = c.type + (c.subType.isEmpty() ? "" : " \u00B7 " + c.subType);
        card.addView(CaseViews.text(this, tp, 17, 0xFF5A6075, false), CaseViews.lp(this, 0, 12));
        card.addView(CaseViews.text(this, "Case no: " + c.number, 16, 0xFF5A6075, false), CaseViews.lp(this, 6, 6));
        card.addView(CaseViews.text(this, "Court: " + c.court, 16, 0xFF5A6075, false), CaseViews.lp(this, 6, 6));
        card.addView(CaseViews.text(this, "Proceeding: " + c.proceeding, 16, 0xFF5A6075, false), CaseViews.lp(this, 6, 6));

        LinearLayout act = new LinearLayout(this);
        act.setOrientation(LinearLayout.HORIZONTAL);
        act.setGravity(Gravity.CENTER_VERTICAL);
        boolean od = overdue(c);
        act.addView(CaseViews.icon(this, R.drawable.ic_calendar, od ? RED : AppTheme.sub(this), 24));
        TextView nx = CaseViews.text(this, "Next: " + (c.next.isEmpty() ? "-" : c.next), 17,
                od ? RED : AppTheme.text(this), true);
        nx.setPadding(LegalUi.dp(this, 8), 0, LegalUi.dp(this, 10), 0);
        act.addView(nx);
        if (od) {
            TextView chip = CaseViews.text(this, "OVERDUE", 13, RED, true);
            chip.setPadding(LegalUi.dp(this, 12), LegalUi.dp(this, 6), LegalUi.dp(this, 12), LegalUi.dp(this, 6));
            chip.setBackground(CaseViews.box(this, 0x00000000, 16, RED, 1));
            act.addView(chip);
        }
        View spacer = new View(this);
        act.addView(spacer, LegalUi.weight());
        act.addView(actIcon(R.drawable.ic_delete, RED, v -> LegalUi.confirm(this, "Delete " + c.title() + "?",
                "Case and hearings are removed.", () -> { store.deleteCase(c.id); render(); })));
        act.addView(actIcon(R.drawable.ic_calendar, 0xFF3F5A73, v -> reschedule(c)));
        act.addView(actIcon(R.drawable.ic_edit, 0xFF3F5A73, v -> {
            Intent i = new Intent(this, AddCaseActivity.class);
            i.putExtra("id", c.id);
            startActivity(i);
        }));
        card.addView(act, CaseViews.lp(this, 12, 0));
        return card;
    }

    private ImageView actIcon(int res, int color, View.OnClickListener click) {
        ImageView v = CaseViews.icon(this, res, color, 26);
        v.setPadding(LegalUi.dp(this, 2), 0, LegalUi.dp(this, 2), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LegalUi.dp(this, 34), LegalUi.dp(this, 30));
        lp.leftMargin = LegalUi.dp(this, 10);
        v.setLayoutParams(lp);
        v.setOnClickListener(click);
        return v;
    }

    private void reschedule(DiaryStore.HCase c) {
        Calendar cal = Calendar.getInstance();
        Date d = DiaryStore.parse(c.next);
        if (d != null) cal.setTime(d);
        new DatePickerDialog(this, (v, y, m, day) -> {
            Calendar x = Calendar.getInstance();
            x.set(y, m, day);
            if (!c.next.isEmpty()) c.prev = c.next;
            c.next = DiaryStore.fmt(x.getTime());
            store.saveCase(c);
            render();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void shareWeek() {
        List<DiaryStore.HCase> list = visible();
        if (list.isEmpty()) {
            Toast.makeText(this, "Nothing to share", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder("Hearings this week\n");
        for (DiaryStore.HCase c : list) {
            sb.append("\n").append(c.title()).append(" - ").append(c.next);
            if (!c.court.isEmpty()) sb.append(" - ").append(c.court);
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(i, "Share"));
    }

    private void notesDialog() {
        LinearLayout f = LegalUi.form(this);
        EditText e = LegalUi.multiline(this, f, "Notes", store.notes());
        e.setMinLines(8);
        LegalUi.formDialog(this, "Notes", f, () -> {
            store.setNotes(e.getText().toString());
            return true;
        }, null);
    }
}
