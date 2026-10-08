package com.example.twobuttons;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Add New Case, 4 steps. Pass extra "id" to edit an existing case.
 * Step 1 parties + number + type. Step 2 court tier, judge, room. Step 3 case info + dates. Step 4 review.
 */
public class AddCaseActivity extends BaseActivity {

    private static final int STEPS = 4;
    private static final int BLACK = 0xFF1C1C1C;

    private DiaryStore store;
    private DiaryStore.HCase c;
    private boolean editing;
    private int step = 1;

    private LinearLayout stepBox, dots, buttons;
    private TextView stepLabel;

    // step widgets
    private EditText first, second, number;
    private Spinner tier, judge;
    private EditText room;
    private List<DiaryStore.Judge> shownJudges = new ArrayList<>();
    private EditText representing, subType, proceeding, prev, next;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new DiaryStore(this);
        String id = getIntent().getStringExtra("id");
        DiaryStore.HCase ex = store.caseById(id);
        editing = ex != null;
        c = editing ? ex : new DiaryStore.HCase();
        setTitle(editing ? "Edit Case" : "Add New Case");

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(AppTheme.bg(this));
        int p = LegalUi.dp(this, 20);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(p, p, p, LegalUi.dp(this, 6));
        stepLabel = CaseViews.text(this, "", 15, AppTheme.sub(this), false);
        head.addView(stepLabel, LegalUi.weight());
        dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        head.addView(dots);
        page.addView(head);

        ScrollView sv = new ScrollView(this);
        stepBox = new LinearLayout(this);
        stepBox.setOrientation(LinearLayout.VERTICAL);
        stepBox.setPadding(p, 0, p, p);
        sv.addView(stepBox);
        page.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(p, LegalUi.dp(this, 8), p, p);
        page.addView(buttons);

        setContentView(page);
        show();
    }

    // ---------- chrome ----------

    private void show() {
        stepLabel.setText("Step " + step + " of " + STEPS);
        dots.removeAllViews();
        for (int i = 1; i <= STEPS; i++) {
            View d = new View(this);
            d.setBackground(CaseViews.box(this, i == step ? BLACK : AppTheme.cardStroke(this), 6, 0, 0));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LegalUi.dp(this, i == step ? 30 : 12), LegalUi.dp(this, 12));
            lp.leftMargin = LegalUi.dp(this, 6);
            dots.addView(d, lp);
        }
        stepBox.removeAllViews();
        if (step == 1) buildStep1();
        else if (step == 2) buildStep2();
        else if (step == 3) buildStep3();
        else buildStep4();
        buildButtons();
    }

    private void buildButtons() {
        buttons.removeAllViews();
        if (step > 1) {
            Button back = btn("\u2190 Back", false);
            back.setOnClickListener(v -> {
                collect(false);
                step--;
                show();
            });
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, LegalUi.dp(this, 54), 1f);
            l.rightMargin = LegalUi.dp(this, 8);
            buttons.addView(back, l);
        }
        Button nx = btn(step == STEPS ? (editing ? "Save changes" : "Save case") : "Next \u2192", true);
        nx.setOnClickListener(v -> {
            if (!collect(true)) return;
            if (step == STEPS) {
                store.saveCase(c);
                Toast.makeText(this, "Case saved", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                step++;
                show();
            }
        });
        buttons.addView(nx, new LinearLayout.LayoutParams(0, LegalUi.dp(this, 54), 1f));
    }

    private Button btn(String label, boolean dark) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setTextColor(dark ? 0xFFFFFFFF : AppTheme.text(this));
        b.setBackground(CaseViews.box(this, dark ? BLACK : AppTheme.card(this), 12, 0, 0));
        return b;
    }

    private TextView label(String s) {
        TextView t = CaseViews.text(this, s, 17, AppTheme.text(this), true);
        t.setPadding(0, LegalUi.dp(this, 18), 0, LegalUi.dp(this, 8));
        return t;
    }

    private TextView helper(String s) {
        TextView t = CaseViews.text(this, s, 14, AppTheme.sub(this), false);
        t.setPadding(0, LegalUi.dp(this, 6), 0, 0);
        return t;
    }

    private EditText field(String hint, String value, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextSize(17);
        e.setPadding(LegalUi.dp(this, 18), LegalUi.dp(this, 14), LegalUi.dp(this, 18), LegalUi.dp(this, 14));
        e.setBackground(CaseViews.box(this, AppTheme.field(this), 12, AppTheme.fieldStroke(this), 1));
        return e;
    }

    private static final int NAME = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS;
    private static final int TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;

    // ---------- step 1 ----------

    private void buildStep1() {
        stepBox.addView(label("First Party Name *"));
        first = field("Enter First and Last Name", c.first, NAME);
        stepBox.addView(first);
        stepBox.addView(helper("Add First Party's full name"));

        stepBox.addView(label("Second Party Name *"));
        second = field("Enter First and Last Name", c.second, NAME);
        stepBox.addView(second);
        stepBox.addView(helper("Add Second Party's full name"));

        stepBox.addView(label("Case Number"));
        number = field("12345/2025", c.number, TEXT);
        stepBox.addView(number);
        stepBox.addView(helper("Enter Case Number"));

        stepBox.addView(label("Case Type *"));
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        for (String t : DiaryStore.CASE_TYPES) {
            boolean on = t.equalsIgnoreCase(c.type);
            TextView chip = CaseViews.text(this, t, 16, on ? 0xFFFFFFFF : AppTheme.sub(this), on);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(LegalUi.dp(this, 14), LegalUi.dp(this, 14), LegalUi.dp(this, 14), LegalUi.dp(this, 14));
            chip.setBackground(CaseViews.box(this, on ? BLACK : AppTheme.card(this), 14, 0, 0));
            chip.setOnClickListener(v -> {
                c.type = t;
                first.clearFocus();
                collectStep1();
                show();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = LegalUi.dp(this, 8);
            chips.addView(chip, lp);
        }
        stepBox.addView(chips);
    }

    private void collectStep1() {
        c.first = LegalUi.str(first);
        c.second = LegalUi.str(second);
        c.number = LegalUi.str(number);
    }

    // ---------- step 2 ----------

    private void buildStep2() {
        stepBox.addView(label("Court Tier *"));
        stepBox.addView(helper("Select from saved tiers, or add a new one."));
        tier = new Spinner(this);
        fillTier();
        stepBox.addView(tier, CaseViews.lp(this, 8, 0));
        TextView addTier = CaseViews.text(this, "+ Add court tier", 14, AppTheme.accent(this), true);
        addTier.setPadding(0, LegalUi.dp(this, 8), 0, 0);
        addTier.setOnClickListener(v -> addTierDialog());
        stepBox.addView(addTier);

        stepBox.addView(label("Judge Name *"));
        stepBox.addView(helper("Judges are filtered by selected court tier."));
        judge = new Spinner(this);
        stepBox.addView(judge, CaseViews.lp(this, 8, 0));
        fillJudges();

        Button addJ = btn("Add Judge", false);
        addJ.setTextSize(16);
        addJ.setBackground(CaseViews.box(this, AppTheme.field(this), 12, AppTheme.fieldStroke(this), 1));
        addJ.setOnClickListener(v -> addJudgeDialog());
        stepBox.addView(addJ, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LegalUi.dp(this, 54)));
        Button manage = btn("Manage judges list", false);
        manage.setTextSize(15);
        manage.setBackground(CaseViews.box(this, AppTheme.field(this), 12, AppTheme.fieldStroke(this), 1));
        manage.setOnClickListener(v -> manageJudges());
        stepBox.addView(manage, CaseViews.lp(this, 12, 0));

        stepBox.addView(label("Court room location"));
        room = field("e.g. Building A, 2nd Floor", c.room, TEXT);
        stepBox.addView(room);
        stepBox.addView(helper("Auto-filled from selected judge when available, and always editable."));

        tier.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                String sel = pos == 0 ? "" : (String) p.getItemAtPosition(pos);
                if (!sel.equals(c.court)) { c.court = sel; c.judge = ""; }
                fillJudges();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void fillTier() {
        List<String> items = new ArrayList<>();
        items.add("Select court tier");
        items.addAll(store.courts());
        tier.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
        int i = items.indexOf(c.court);
        tier.setSelection(i < 0 ? 0 : i);
    }

    private void fillJudges() {
        shownJudges.clear();
        List<String> items = new ArrayList<>();
        if (c.court.isEmpty()) {
            items.add("Select court tier first");
        } else {
            items.add("Select judge");
            for (DiaryStore.Judge j : store.judges()) {
                if (j.court.equals(c.court)) { shownJudges.add(j); items.add(j.name); }
            }
        }
        judge.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
        int i = items.indexOf(c.judge);
        judge.setSelection(i < 0 ? 0 : i);
        judge.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos > 0 && pos - 1 < shownJudges.size()) {
                    DiaryStore.Judge j = shownJudges.get(pos - 1);
                    if (!j.name.equals(c.judge) && !j.room.isEmpty() && room != null) room.setText(j.room);
                    c.judge = j.name;
                } else if (pos == 0) {
                    c.judge = "";
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void addTierDialog() {
        LinearLayout f = LegalUi.form(this);
        EditText name = LegalUi.input(this, f, "Court tier name", "", NAME);
        LegalUi.formDialog(this, "Add court tier", f, () -> {
            String s = LegalUi.str(name);
            if (!LegalUi.need(this, s, "Name")) return false;
            List<String> l = store.courts();
            l.add(s);
            store.saveCourts(l);
            c.court = s;
            c.judge = "";
            fillTier();
            return true;
        }, null);
    }

    private void addJudgeDialog() {
        if (c.court.isEmpty()) {
            Toast.makeText(this, "Select court tier first", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout f = LegalUi.form(this);
        EditText name = LegalUi.input(this, f, "Judge name", "", NAME);
        EditText rm = LegalUi.input(this, f, "Court room location", "", TEXT);
        LegalUi.formDialog(this, "Add judge (" + c.court + ")", f, () -> {
            String s = LegalUi.str(name);
            if (!LegalUi.need(this, s, "Judge name")) return false;
            DiaryStore.Judge j = new DiaryStore.Judge();
            j.name = s;
            j.court = c.court;
            j.room = LegalUi.str(rm);
            List<DiaryStore.Judge> l = store.judges();
            l.add(j);
            store.saveJudges(l);
            c.judge = j.name;
            if (!j.room.isEmpty()) room.setText(j.room);
            fillJudges();
            return true;
        }, null);
    }

    private void manageJudges() {
        List<DiaryStore.Judge> all = store.judges();
        if (all.isEmpty()) {
            Toast.makeText(this, "No judges saved", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[all.size()];
        for (int i = 0; i < names.length; i++) names[i] = all.get(i).name + "  (" + all.get(i).court + ")";
        new AlertDialog.Builder(this)
                .setTitle("Tap a judge to delete")
                .setItems(names, (d, w) -> LegalUi.confirm(this, "Delete " + all.get(w).name + "?",
                        "This cannot be undone.", () -> {
                            all.remove(w);
                            store.saveJudges(all);
                            fillJudges();
                        }))
                .setNegativeButton("Close", null)
                .show();
    }

    // ---------- step 3 (assumed layout) ----------

    private void buildStep3() {
        stepBox.addView(label("Representing"));
        representing = field("Client name", c.representing, NAME);
        stepBox.addView(representing);
        stepBox.addView(label("Case sub type"));
        subType = field("e.g. Writ", c.subType, TEXT);
        stepBox.addView(subType);
        stepBox.addView(label("Proceeding"));
        proceeding = field("Current proceeding", c.proceeding, TEXT);
        stepBox.addView(proceeding);
        stepBox.addView(label("Previous hearing date"));
        prev = field("dd/MM/yyyy", c.prev, InputType.TYPE_NULL);
        prev.setFocusable(false);
        prev.setOnClickListener(v -> LegalUi.pickDmy(this, prev));
        stepBox.addView(prev);
        stepBox.addView(label("Next hearing date"));
        next = field("dd/MM/yyyy", c.next, InputType.TYPE_NULL);
        next.setFocusable(false);
        next.setOnClickListener(v -> LegalUi.pickDmy(this, next));
        stepBox.addView(next);
    }

    // ---------- step 4 ----------

    private void buildStep4() {
        stepBox.addView(label("Review"));
        LinearLayout card = LegalUi.card(this);
        card.addView(row("Case", c.title()));
        card.addView(row("Case No", c.number));
        card.addView(row("Type", c.type + (c.subType.isEmpty() ? "" : " \u00B7 " + c.subType)));
        card.addView(row("Court", c.court));
        card.addView(row("Judge", c.judge));
        card.addView(row("Room", c.room));
        card.addView(row("Representing", c.representing));
        card.addView(row("Proceeding", c.proceeding));
        card.addView(row("Previous", c.prev));
        card.addView(row("Next", c.next));
        stepBox.addView(card);
    }

    private LinearLayout row(String k, String v) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, LegalUi.dp(this, 5), 0, LegalUi.dp(this, 5));
        r.addView(CaseViews.text(this, k, 14, AppTheme.sub(this), false),
                new LinearLayout.LayoutParams(LegalUi.dp(this, 100), LinearLayout.LayoutParams.WRAP_CONTENT));
        r.addView(CaseViews.text(this, v == null || v.isEmpty() ? "-" : v, 15, AppTheme.text(this), false), LegalUi.weight());
        return r;
    }

    // ---------- collect + validate ----------

    private boolean collect(boolean validate) {
        if (step == 1) {
            collectStep1();
            if (!validate) return true;
            if (!LegalUi.need(this, c.first, "First party name")) return false;
            if (!LegalUi.need(this, c.second, "Second party name")) return false;
            if (!LegalUi.need(this, c.type, "Case type")) return false;
        } else if (step == 2) {
            c.room = LegalUi.str(room);
            if (validate && !validateStep2()) return false;
        } else if (step == 3) {
            c.representing = LegalUi.str(representing);
            c.subType = LegalUi.str(subType);
            c.proceeding = LegalUi.str(proceeding);
            c.prev = LegalUi.str(prev);
            c.next = LegalUi.str(next);
        }
        return true;
    }

    private boolean validateStep2() {
        if (!LegalUi.need(this, c.court, "Court tier")) return false;
        if (!LegalUi.need(this, c.judge, "Judge name")) return false;
        return true;
    }
}
