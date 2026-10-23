package com.example.twobuttons;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** All cases: search, status chips, customizable cards, filter sheet. */
public class AllCasesActivity extends BaseActivity {

    // ---- status colours (also used by the colour guide) ----
    private static final int C_TODAY = 0xFFD32F2F;
    private static final int C_UPCOMING = 0xFF2E7D32;
    private static final int C_NEEDS = 0xFFF59E0B;
    private static final int C_NONE = 0xFF9CA3AF;
    private static final int C_DISPOSED = 0xFF64748B;
    private static final int GOLD = 0xFFF5B400;

    private static final String[] SORTS = {
            "Newest first", "Oldest first", "Hearing date (soonest)",
            "Hearing date (latest)", "Case title (A to Z)", "Case title (Z to A)"};
    private static final String[] KINDS = {
            "All courts", "District Court", "High Court", "Supreme Court",
            "Tribunal (NCLT)", "Consumer Court", "Revenue Court"};
    static final String[] STAGES = {
            "All stages", "Filing", "Registration", "Notice", "Appearance", "Summons / Service",
            "Written Statement", "Replication", "Admission / Denial of Documents", "Framing of Issues",
            "Plaintiff Evidence", "Defendant Evidence", "Cross Examination", "Arguments",
            "Final Arguments", "Reserved for Orders", "Judgment", "Order", "Decree",
            "Bail", "Charge Framing", "Prosecution Evidence", "Statement under Section 313",
            "Defence Evidence", "Execution", "Appeal", "Mediation / Settlement", "Interim Application",
            "Stay Application", "Compliance", "For Reply", "For Hearing", "Other"};

    // customize-card toggles: key, label, locked, default
    private static final Object[][] OPTIONS = {
            {"title", "Case title", true, true},
            {"hearing", "Hearing status and date", true, true},
            {"number", "Case number", false, true},
            {"category", "Court category", false, true},
            {"judge", "Judge name", false, true},
            {"court", "Court type / name", false, false},
            {"client", "Client name", false, true},
            {"station", "Police station", false, true},
            {"fir", "FIR number", false, true},
            {"firdate", "FIR date", false, false},
            {"acts", "Acts and sections", false, true},
            {"star", "Important case star", false, true}};

    private DiaryStore store;
    private LegalStore legal;
    private SharedPreferences prefs;
    private LinearLayout list;
    private EditText search;
    private LinearLayout chipRow;
    private String tab = "All";

    // filters
    private int sortIdx = 0;
    private String fKind = KINDS[0];
    private String fStage = StageStore.ALL;
    private String fTitle = "", fCnr = "", fCourt = "", fNo = "", fFiling = "", fYear = "";
    private int fCnrMode = 0;   // 0 any, 1 has CNR, 2 no CNR

    private int dp(int v) { return CaseViews.dp(this, v); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new DiaryStore(this);
        legal = new LegalStore(this);
        prefs = getSharedPreferences("all_cases_prefs", MODE_PRIVATE);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(AppTheme.bg(this));

        // ---- top bar ----
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(10), dp(12), dp(6));
        ImageView back = CaseViews.icon(this, R.drawable.ic_arrow_back, AppTheme.text(this), 24);
        back.setPadding(dp(8), dp(8), dp(8), dp(8));
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView title = CaseViews.text(this, "Cases", 20, AppTheme.text(this), true);
        title.setPadding(dp(6), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView gear = CaseViews.icon(this, R.drawable.ic_settings, AppTheme.text(this), 22);
        gear.setPadding(dp(9), dp(9), dp(9), dp(9));
        gear.setContentDescription("Customize case cards");
        gear.setOnClickListener(v -> customizeSheet());
        bar.addView(gear, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView add = CaseViews.text(this, "+ New case", 13, 0xFFFFFFFF, true);
        add.setGravity(Gravity.CENTER);
        add.setPadding(dp(14), dp(8), dp(14), dp(8));
        add.setBackground(CaseViews.box(this, AppTheme.accent(this), 20, 0, 0));
        if (AppTheme.isDark(this)) add.setTextColor(0xFF121212);
        add.setOnClickListener(v -> startActivity(new Intent(this, NewCaseActivity.class)));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.leftMargin = dp(6);
        bar.addView(add, alp);
        page.addView(bar);

        // ---- search ----
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(14), 0, dp(6), 0);
        pill.setBackground(CaseViews.box(this, AppTheme.field(this), 14, AppTheme.fieldStroke(this), 1));
        pill.addView(CaseViews.icon(this, R.drawable.ic_search, AppTheme.sub(this), 20));
        search = new RoundEdit(this);
        FieldStyle.hint(search, "Search cases, CNR, client…");
        search.setHintTextColor(AppTheme.sub(this));
        search.setTextColor(AppTheme.text(this));
        search.setTextSize(14);
        search.setSingleLine(true);
        search.setBackground(null);
        search.setPadding(dp(10), dp(12), dp(6), dp(12));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { render(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        pill.addView(search, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView fil = CaseViews.icon(this, R.drawable.ic_filter, AppTheme.accent(this), 22);
        fil.setPadding(dp(9), dp(9), dp(9), dp(9));
        fil.setContentDescription("Filter cases");
        fil.setOnClickListener(v -> filterSheet());
        pill.addView(fil, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.leftMargin = dp(16);
        plp.rightMargin = dp(16);
        page.addView(pill, plp);

        // ---- chips ----
        chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(dp(16), dp(12), dp(16), dp(6));
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chipRow);
        page.addView(hs);

        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(8), dp(16), dp(24));
        sv.addView(list);
        page.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    // ------------------------------------------------------------ data helpers

    private static Date todayStart() { return DiaryStore.today(); }

    /** Next hearing date: the case's own next date, else the earliest future hearing entry. */
    private static Date nextHearing(DiaryStore.HCase h) {
        Date n = DiaryStore.parse(h.next);
        if (n != null) return n;
        Date best = null;
        Date t = todayStart();
        for (DiaryStore.Hearing x : h.hearings) {
            Date d = DiaryStore.parse(x.date);
            if (d != null && !d.before(t) && (best == null || d.before(best))) best = d;
        }
        return best;
    }

    /** today / upcoming / needs / none / disposed */
    private static String status(DiaryStore.HCase h) {
        if (h.completed) return "disposed";
        Date n = nextHearing(h);
        if (n == null) return "none";
        Date t = todayStart();
        if (n.before(t)) return "needs";
        if (n.equals(t)) return "today";
        return "upcoming";
    }

    private static int statusColor(String s) {
        switch (s) {
            case "today": return C_TODAY;
            case "upcoming": return C_UPCOMING;
            case "needs": return C_NEEDS;
            case "disposed": return C_DISPOSED;
            default: return C_NONE;
        }
    }

    private String clientName(DiaryStore.HCase h) {
        if (h.clientId == null || h.clientId.isEmpty()) return "";
        LegalStore.Client c = legal.client(h.clientId);
        return c == null ? "" : c.name;
    }

    private static boolean has(String hay, String needle) {
        return hay != null && hay.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private boolean matches(DiaryStore.HCase h) {
        String st = status(h);
        if (tab.equals("Needs update") && !st.equals("needs")) return false;
        if (tab.equals("Upcoming") && !(st.equals("upcoming") || st.equals("today"))) return false;
        if (tab.equals("Disposed") && !st.equals("disposed")) return false;

        String q = search.getText().toString().trim();
        if (!q.isEmpty()) {
            String all = h.title() + " " + h.cnr + " " + clientName(h) + " " + h.number + " " + h.court
                    + " " + h.judge + " " + h.filingNo + " " + h.firNo + " " + h.firYear + " " + h.firSections + " " + h.acts;
            if (!has(all, q)) return false;
        }
        if (!fKind.equals(KINDS[0])) {
            String k = fKind.startsWith("Tribunal") ? "Tribunal" : fKind;
            if (!has(h.courtKind, k)) return false;
        }
        if (!fStage.equals(StageStore.ALL) && !has(h.proceeding, fStage)) return false;
        if (!fTitle.isEmpty() && !has(h.title(), fTitle)) return false;
        if (!fCnr.isEmpty() && !has(h.cnr, fCnr)) return false;
        if (!fCourt.isEmpty() && !has(h.court, fCourt)) return false;
        if (!fNo.isEmpty() && !has(h.number, fNo)) return false;
        if (!fFiling.isEmpty() && !has(h.filingNo, fFiling)) return false;
        if (!fYear.isEmpty() && !has(h.regDate, fYear) && !has(h.number, fYear)) return false;
        boolean hasCnr = h.cnr != null && !h.cnr.trim().isEmpty();
        if (fCnrMode == 1 && !hasCnr) return false;
        if (fCnrMode == 2 && hasCnr) return false;
        return true;
    }

    private boolean filtersActive() {
        return sortIdx != 0 || !fKind.equals(KINDS[0]) || !fStage.equals(StageStore.ALL)
                || !fTitle.isEmpty() || !fCnr.isEmpty() || !fCourt.isEmpty() || !fNo.isEmpty()
                || !fFiling.isEmpty() || !fYear.isEmpty() || fCnrMode != 0;
    }

    private boolean on(String key) {
        for (Object[] o : OPTIONS) {
            if (o[0].equals(key)) return prefs.getBoolean("show_" + key, (Boolean) o[3]);
        }
        return true;
    }

    private Set<String> stars() {
        return new HashSet<>(prefs.getStringSet("stars", new HashSet<String>()));
    }

    // ------------------------------------------------------------ rendering

    private void render() {
        if (list == null) return;

        chipRow.removeAllViews();
        for (String t : new String[]{"All", "Needs update", "Upcoming", "Disposed"}) {
            boolean sel = t.equals(tab);
            TextView c = CaseViews.text(this, t, 13, sel ? (AppTheme.isDark(this) ? 0xFF121212 : 0xFFFFFFFF)
                    : AppTheme.text(this), sel);
            c.setGravity(Gravity.CENTER);
            c.setPadding(dp(16), dp(8), dp(16), dp(8));
            c.setBackground(CaseViews.box(this, sel ? AppTheme.accent(this) : AppTheme.bg(this), 20,
                    sel ? AppTheme.accent(this) : AppTheme.fieldStroke(this), 1));
            c.setOnClickListener(v -> { tab = t; render(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            chipRow.addView(c, lp);
        }

        list.removeAllViews();
        List<DiaryStore.HCase> all = store.cases();
        List<DiaryStore.HCase> out = new ArrayList<>();
        // store order is oldest-first; remember it for "newest / oldest"
        for (DiaryStore.HCase h : all) if (matches(h)) out.add(h);
        final List<DiaryStore.HCase> order = all;
        Collections.sort(out, (a, b) -> {
            switch (sortIdx) {
                case 1: return order.indexOf(a) - order.indexOf(b);
                case 2: return cmpDate(nextHearing(a), nextHearing(b), true);
                case 3: return cmpDate(nextHearing(a), nextHearing(b), false);
                case 4: return a.title().compareToIgnoreCase(b.title());
                case 5: return b.title().compareToIgnoreCase(a.title());
                default: return order.indexOf(b) - order.indexOf(a);
            }
        });

        TextView count = CaseViews.text(this, out.size() + (out.size() == 1 ? " case" : " cases")
                + (filtersActive() ? "  •  filters on" : ""), 12, AppTheme.sub(this), false);
        list.addView(count, CaseViews.lp(this, 0, 8));

        if (out.isEmpty()) {
            list.addView(CaseViews.emptyNote(this, "No cases found"));
            return;
        }
        for (DiaryStore.HCase h : out) list.addView(card(h), CaseViews.lp(this, 0, 12));
    }

    /** Cases without a date always go last. */
    private static int cmpDate(Date a, Date b, boolean soonestFirst) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return soonestFirst ? a.compareTo(b) : b.compareTo(a);
    }

    private TextView line(String s) {
        TextView t = CaseViews.text(this, s, 13, AppTheme.sub(this), false);
        t.setPadding(0, dp(3), 0, 0);
        return t;
    }

    private void openCase(DiaryStore.HCase h) {
        Intent i = new Intent(this, HearingsActivity.class);
        i.putExtra("id", h.id);
        startActivity(i);
    }

    private View card(DiaryStore.HCase h) {
        String st = status(h);
        String t = h.title().trim();
        AppCard c = new AppCard(this)
                .avatarText(t.isEmpty() ? "?" : t.substring(0, 1).toUpperCase(Locale.ROOT));
        c.title(h.title());
        if (on("number") && !h.number.isEmpty()) c.subtitle(h.number);
        if (on("court") && !h.court.isEmpty()) c.line(h.court);
        if (on("judge") && !h.judge.isEmpty()) c.line("Judge: " + h.judge);
        if (on("judge") && !h.room.isEmpty()) c.line("Court room: " + h.room);
        if (!h.clientRole.isEmpty()) c.line("My client: " + h.clientRole);
        if (!h.nextStatus.isEmpty()) c.line("Next status: " + h.nextStatus);
        String cn = clientName(h);
        if (on("client") && !cn.isEmpty()) c.line("Client: " + cn);
        if (on("station") && !h.firStation.isEmpty()) c.line("Police station: " + h.firStation);
        if (on("fir") && !h.firNo.isEmpty()) c.line("FIR No: " + h.firNo + (h.firYear.isEmpty() ? "" : "/" + h.firYear));
        if (on("fir") && !h.firSections.isEmpty()) c.line("FIR u/s: " + h.firSections);
        if (on("firdate") && !h.firDate.isEmpty()) c.line("FIR date: " + h.firDate);
        if (on("acts") && !h.acts.isEmpty()) c.line("Acts: " + h.acts);
        if (on("category") && !h.courtKind.isEmpty()) c.chip(h.courtKind, AppCard.Tone.ACCENT);

        switch (st) {
            case "needs": c.chip("Needs update", AppCard.Tone.WARN); break;
            case "disposed": c.chip("Disposed", AppCard.Tone.GOOD); break;
            case "today": c.chip("Today", AppCard.Tone.BAD); break;
            case "upcoming": c.chip("Upcoming", AppCard.Tone.NEUTRAL); break;
            default: break;
        }

        if (on("star")) {
            Set<String> s = stars();
            boolean starred = s.contains(h.id);
            ImageView star = CaseViews.icon(this, R.drawable.ic_star, starred ? GOLD : AppTheme.cardStroke(this), 24);
            star.setPadding(dp(4), dp(4), dp(4), dp(4));
            star.setContentDescription("Mark important");
            star.setOnClickListener(v -> {
                Set<String> n = stars();
                if (!n.remove(h.id)) n.add(h.id);
                prefs.edit().putStringSet("stars", n).apply();
                render();
            });
            c.topRightView(star);
        }

        Date nh = nextHearing(h);
        String hs;
        if (st.equals("disposed")) hs = "Disposed";
        else if (nh == null) hs = "No hearing scheduled";
        else hs = DiaryStore.fmt(nh);
        String cap = st.equals("disposed") || nh == null ? ""
                : (st.equals("needs") ? "Needs update, last hearing" : "Next hearing");
        c.footer(hs, cap, statusColor(st));
        if (!st.equals("disposed")) c.secondary("Add hearing", v -> openCase(h));
        c.primary("Details", v -> openCase(h));
        c.onClick(v -> openCase(h));
        return c.build();
    }

    // ------------------------------------------------------------ bottom sheets

    private static final int BLUE = 0xFF3B82F6;

    private int ink() { return PickerDialogs.ink(this); }
    private int soft() { return PickerDialogs.soft(this); }
    private int hair() { return PickerDialogs.hair(this); }

    private static boolean dark(Context c) { return AppTheme.isDark(c); }

    /** Bottom sheet: title row, scrollable body, footer with two buttons. Returns the body. */
    private LinearLayout sheet(String title, String leftLabel, Runnable left,
                               String rightLabel, Runnable right, Dialog[] holder) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        holder[0] = d;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(PickerDialogs.panel(this, 28, 0, true));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(22), dp(20), dp(14), dp(8));
        head.addView(CaseViews.text(this, title, 20, ink(), true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView x = CaseViews.icon(this, R.drawable.ic_close, ink(), 22);
        x.setPadding(dp(8), dp(8), dp(8), dp(8));
        x.setOnClickListener(v -> d.dismiss());
        head.addView(x, new LinearLayout.LayoutParams(dp(40), dp(40)));
        root.addView(head);

        ScrollView sv = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(4), dp(22), dp(12));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setPadding(dp(22), dp(10), dp(22), dp(20));
        TextView l = CaseViews.text(this, leftLabel, 14, ink(), true);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, dp(13), 0, dp(13));
        l.setBackground(CaseViews.box(this, PickerDialogs.chip(this), 26, hair(), 1));
        l.setOnClickListener(v -> left.run());
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        llp.rightMargin = dp(10);
        foot.addView(l, llp);
        TextView rt = CaseViews.text(this, rightLabel, 14, 0xFFFFFFFF, true);
        rt.setGravity(Gravity.CENTER);
        rt.setPadding(0, dp(13), 0, dp(13));
        rt.setBackground(CaseViews.box(this, BLUE, 26, 0, 0));
        rt.setOnClickListener(v -> right.run());
        foot.addView(rt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(foot);

        d.setContentView(root);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(0.65f);
            DisplayMetrics dm = getResources().getDisplayMetrics();
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (int) (dm.heightPixels * 0.9f));
            w.setGravity(Gravity.BOTTOM);
        }
        return body;
    }

    // ---- Customize case cards ----

    private void customizeSheet() {
        final boolean[] vals = new boolean[OPTIONS.length];
        for (int i = 0; i < OPTIONS.length; i++) {
            vals[i] = prefs.getBoolean("show_" + OPTIONS[i][0], (Boolean) OPTIONS[i][3]);
        }
        final Dialog[] h = new Dialog[1];
        final Switch[] switches = new Switch[OPTIONS.length];
        LinearLayout body = sheet("Customize case cards", "Restore defaults", () -> {
            for (int i = 0; i < OPTIONS.length; i++) {
                vals[i] = (Boolean) OPTIONS[i][3];
                if (switches[i] != null) switches[i].setChecked(vals[i]);
            }
        }, "Save", () -> {
            SharedPreferences.Editor e = prefs.edit();
            for (int i = 0; i < OPTIONS.length; i++) e.putBoolean("show_" + OPTIONS[i][0], vals[i]);
            e.apply();
            h[0].dismiss();
            render();
        }, h);

        body.addView(CaseViews.text(this, "Choose what shows on each case card.", 14,
                soft(), false), CaseViews.lp(this, 0, 8));
        for (int i = 0; i < OPTIONS.length; i++) {
            final int idx = i;
            boolean locked = (Boolean) OPTIONS[i][2];
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, dp(10));
            LinearLayout txt = new LinearLayout(this);
            txt.setOrientation(LinearLayout.VERTICAL);
            txt.addView(CaseViews.text(this, (String) OPTIONS[i][1], 15, ink(), false));
            if (locked) txt.addView(CaseViews.text(this, "Always shown", 11, soft(), false));
            row.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (locked) {
                ImageView lock = CaseViews.icon(this, R.drawable.ic_lock, soft(), 20);
                row.addView(lock, new LinearLayout.LayoutParams(dp(20), dp(20)));
                row.setPadding(0, dp(10), dp(4), dp(10));
                View lockLine = new View(this);
                lockLine.setBackgroundColor(hair());
                body.addView(row);
                body.addView(lockLine, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
                continue;
            }
            Switch sw = new Switch(this);
            sw.setChecked(vals[i]);
            sw.setEnabled(!locked);
            android.content.res.ColorStateList track = new android.content.res.ColorStateList(
                    new int[][]{{android.R.attr.state_checked}, {}},
                    new int[]{0x883B82F6, dark(this) ? 0x44FFFFFF : 0x33000000});
            android.content.res.ColorStateList thumb = new android.content.res.ColorStateList(
                    new int[][]{{android.R.attr.state_checked}, {}},
                    new int[]{BLUE, dark(this) ? 0xFFC4CAE0 : 0xFFF1F1F1});
            sw.setTrackTintList(track);
            sw.setThumbTintList(thumb);
            sw.setOnCheckedChangeListener((b, on) -> vals[idx] = on);
            switches[i] = sw;
            row.addView(sw);
            body.addView(row);
            View line = new View(this);
            line.setBackgroundColor(hair());
            body.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
    }

    // ---- Filter cases ----

    /** Collapsible section: returns its body. */
    private LinearLayout section(LinearLayout parent, String title, boolean openAtStart) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(14), 0, dp(10));
        head.addView(CaseViews.text(this, title, 16, ink(), true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageView arrow = CaseViews.icon(this, R.drawable.ic_expand_more, ink(), 22);
        head.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(24)));
        parent.addView(head);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(openAtStart ? View.VISIBLE : View.GONE);
        arrow.setRotation(openAtStart ? 180f : 0f);
        parent.addView(body);
        head.setOnClickListener(v -> {
            boolean show = body.getVisibility() != View.VISIBLE;
            body.setVisibility(show ? View.VISIBLE : View.GONE);
            arrow.setRotation(show ? 180f : 0f);
        });
        View line = new View(this);
        line.setBackgroundColor(hair());
        parent.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return body;
    }

    /** Single-choice rows with a round selector on the right. cur[0] holds the selected index. */
    private void radios(LinearLayout body, String[] opts, int[] cur) {
        body.removeAllViews();
        for (int i = 0; i < opts.length; i++) {
            final int idx = i;
            boolean sel = cur[0] == i;
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setMinimumHeight(dp(44));
            r.setPadding(dp(16), dp(6), dp(16), dp(6));
            int fill = sel ? (dark(this) ? 0xFF1B2A4E : 0xFFE8F0FE) : (dark(this) ? 0xFF0D1428 : 0xFFF4F5FA);
            r.setBackground(CaseViews.box(this, fill, 16, sel ? BLUE : hair(), 1));
            r.addView(CaseViews.text(this, opts[i], 14, ink(), sel),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (sel) {
                r.addView(CaseViews.icon(this, R.drawable.ic_check_circle, BLUE, 26));
            } else {
                View ring = new View(this);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setStroke(dp(2), dark(this) ? 0x66FFFFFF : 0x55000000);
                ring.setBackground(g);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(dp(22), dp(22));
                rl.rightMargin = dp(2);
                r.addView(ring, rl);
            }
            r.setOnClickListener(v -> { cur[0] = idx; radios(body, opts, cur); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            body.addView(r, lp);
        }
    }

    private EditText field(LinearLayout body, String label, String value, int inputType) {
        TextView lb = CaseViews.fieldLabel(this, label);
        lb.setTextColor(soft());
        body.addView(lb);
        EditText e = new RoundEdit(this);
        e.setText(value);
        e.setSingleLine(true);
        e.setInputType(inputType);
        body.addView(e, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private void filterSheet() {
        final Dialog[] h = new Dialog[1];
        final int[] sort = {sortIdx};
        final int[] kind = {indexOf(KINDS, fKind)};
        final String[] stageOpts = new StageStore(this).withAll();
        final int[] stage = {Math.max(0, indexOf(stageOpts, fStage))};
        final int[] cnrMode = {fCnrMode};
        final EditText[] f = new EditText[6];
        final Runnable[] resetUi = new Runnable[1];

        LinearLayout body = sheet("Filter cases", "Reset", () -> resetUi[0].run(), "Apply filters", () -> {
            sortIdx = sort[0];
            fKind = KINDS[kind[0]];
            fStage = stageOpts[Math.max(0, stage[0])];
            fTitle = f[0].getText().toString().trim();
            fCnr = f[1].getText().toString().trim();
            fCourt = f[2].getText().toString().trim();
            fNo = f[3].getText().toString().trim();
            fFiling = f[4].getText().toString().trim();
            fYear = f[5].getText().toString().trim();
            fCnrMode = cnrMode[0];
            h[0].dismiss();
            render();
        }, h);

        LinearLayout sortBody = section(body, "Sort cases", false);
        radios(sortBody, SORTS, sort);
        LinearLayout kindBody = section(body, "Court type", false);
        radios(kindBody, KINDS, kind);
        LinearLayout stageBody = section(body, "Hearing stage", false);
        radios(stageBody, stageOpts, stage);

        LinearLayout det = section(body, "Case details", false);
        f[0] = field(det, "Case title", fTitle, InputType.TYPE_CLASS_TEXT);
        f[1] = field(det, "CNR number", fCnr, InputType.TYPE_CLASS_TEXT);
        f[2] = field(det, "Court name", fCourt, InputType.TYPE_CLASS_TEXT);
        f[3] = field(det, "Case number", fNo, InputType.TYPE_CLASS_TEXT);
        f[4] = field(det, "Filing number", fFiling, InputType.TYPE_CLASS_TEXT);
        f[5] = field(det, "Reg. year", fYear, InputType.TYPE_CLASS_NUMBER);
        det.addView(CaseViews.text(this, "CNR number", 13, soft(), true), CaseViews.lp(this, 14, 6));
        LinearLayout cnrBody = new LinearLayout(this);
        cnrBody.setOrientation(LinearLayout.VERTICAL);
        det.addView(cnrBody);
        final String[] cnrOpts = {"Any", "Has CNR", "No CNR"};
        radios(cnrBody, cnrOpts, cnrMode);

        LinearLayout guide = section(body, "Status color guide", false);
        addLegend(guide, C_TODAY, "Hearing today");
        addLegend(guide, C_UPCOMING, "Upcoming hearing");
        addLegend(guide, C_NEEDS, "Needs update (date has passed)");
        addLegend(guide, C_NONE, "No hearing scheduled");
        addLegend(guide, C_DISPOSED, "Disposed");

        resetUi[0] = () -> {
            sort[0] = 0; kind[0] = 0; stage[0] = 0; cnrMode[0] = 0;
            for (EditText e : f) e.setText("");
            radios(sortBody, SORTS, sort);
            radios(kindBody, KINDS, kind);
            radios(stageBody, stageOpts, stage);
            radios(cnrBody, cnrOpts, cnrMode);
        };
    }

    private void addLegend(LinearLayout parent, int color, String label) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(8), 0, dp(8));
        View sw = new View(this);
        sw.setBackground(CaseViews.box(this, color, 4, 0, 0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(8), dp(22));
        lp.rightMargin = dp(12);
        r.addView(sw, lp);
        r.addView(CaseViews.text(this, label, 14, ink(), false));
        parent.addView(r);
    }

    private static int indexOf(String[] a, String v) {
        for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i;
        return 0;
    }
}
