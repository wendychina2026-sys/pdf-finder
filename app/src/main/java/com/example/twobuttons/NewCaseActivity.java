package com.example.twobuttons;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * New Case page: auto-fill (CNR) or manual entry tabs, optional client, the case details (including
 * the court), collapsible additional details (identifiers, parties, fee, notes, FIR, acts) and an
 * upload box for case files (PDF or image).
 */
public class NewCaseActivity extends BaseActivity {

    private static final int REQ_CLIENT = 51;
    private static final int REQ_FILE = 52;
    private static final int REQUIRED = 5;
    /** Everything on this page is drawn a little smaller than the shared default sizes. */
    private static final float TEXT_SCALE = 0.9f;
    private static final String[] COURT_KINDS = {"District Court", "High Court", "Supreme Court",
            "Tribunal (NCLT)", "Consumer Court"};
    private static final int[] COURT_ICONS = {R.drawable.ic_home, R.drawable.ic_briefcase,
            R.drawable.ic_shield, R.drawable.ic_award, R.drawable.ic_bag};
    /** Court types offered before any the user has added themselves. */
    private static final String[] DEFAULT_COURT_TYPES = {"CJM Court", "Family Court", "Consumer Court",
            "Labour Court", "MACT", "Lok Adalat", "POCSO Court", "Income Tax Tribunal", "Special Court",
            "ADJ (Additional District Judge)", "District and Session Judge", "Revenue Court",
            "Addl. District and Session Judge", "Commissioner", "Addl. Commissioner", "SDO Court", "SDJM"};
    private static final String[] PRIORITIES = {"Normal", "High", "Urgent"};
    private static final int YELLOW = 0xFFF9BC24;
    private static final int DARK = 0xFF1C1C1C;
    private static final int NAME = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS;
    private static final int TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
    private static final int CAPS = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;

    private DiaryStore store;
    private LegalStore legal;

    private String courtKind = COURT_KINDS[0];
    private String courtName = "";
    private String caseType = "";
    private String clientId = "";
    private boolean autoMode = true;

    private TextView kindText, courtNameText, caseTypeText, cnrTitle, progressText, remainText;
    private LinearLayout cnrCard, clientBox, actsBox, segAuto, segManual;
    private View progFill, progRest;
    private EditText cnrLookup, titleEdit, caseNoEdit, regEdit;
    private EditText cnrId, filingEdit, diaryEdit, petEdit, resEdit, appearEdit, feeEdit, feeNotesEdit;
    private EditText judgeEdit, subTypeEdit, notesEdit, stationEdit, firNoEdit, firDateEdit;
    private Spinner priority;
    private final List<EditText[]> actRows = new ArrayList<>();
    private final List<Uri> pickedUris = new ArrayList<>();
    private final List<String> pickedNames = new ArrayList<>();
    private LinearLayout filesBox;
    private boolean scaled = false;

    private final TextWatcher watcher = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void afterTextChanged(Editable s) { refresh(); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().hide();
        store = new DiaryStore(this);
        legal = new LegalStore(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AppTheme.bg(this));
        root.addView(header());

        ScrollView sv = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(8), dp(16), dp(24));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        body.addView(toggleBar(), cardLp());
        cnrCard = buildCnrCard();
        body.addView(cnrCard, cardLp());
        body.addView(buildClientCard(), cardLp());
        body.addView(buildCaseCard(), cardLp());
        body.addView(buildAdditionalCard(), cardLp());
        body.addView(buildUploadCard(), cardLp());

        root.addView(bottomBar());
        setContentView(root);
        styleToggle();
        renderClient();
        refresh();
        shrink(root);
        scaled = true;
    }

    // ---------- small helpers ----------

    /** Scales every text on this page down a little; views added later call this for themselves. */
    private void shrink(View v) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, t.getTextSize() * TEXT_SCALE);
        }
        if (v instanceof ViewGroup && !(v instanceof AdapterView)) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) shrink(g.getChildAt(i));
        }
    }

    private int dp(int v) { return CaseViews.dp(this, v); }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        return lp;
    }

    private LinearLayout.LayoutParams lpTop(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        return lp;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setBackground(CaseViews.box(this, AppTheme.card(this), 18, AppTheme.cardStroke(this), 1));
        return c;
    }

    private LinearLayout tile(int iconRes) {
        LinearLayout t = new LinearLayout(this);
        t.setGravity(Gravity.CENTER);
        t.setBackground(CaseViews.box(this, AppTheme.field(this), 12, 0, 0));
        t.addView(CaseViews.icon(this, iconRes, AppTheme.accent(this), 22));
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        return t;
    }

    private TextView lbl(String s) {
        TextView t = CaseViews.text(this, s, 12, AppTheme.sub(this), true);
        t.setPadding(0, dp(10), 0, dp(4));
        return t;
    }

    private LinearLayout col(String title, int titleSp, String sub) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.addView(CaseViews.text(this, title, titleSp, AppTheme.text(this), false));
        if (sub != null) {
            TextView s = CaseViews.text(this, sub, 12, AppTheme.sub(this), false);
            s.setPadding(0, dp(2), 0, 0);
            c.addView(s);
        }
        return c;
    }

    private void choose(String title, List<String> opts, Consumer<String> cb) {
        new AlertDialog.Builder(this).setTitle(title)
                .setItems(opts.toArray(new String[0]), (d, i) -> cb.accept(opts.get(i)))
                .show();
    }

    /** Dropdown-looking row; the caller keeps the returned TextView to show the chosen value. */
    private TextView dropdown(LinearLayout parent, String placeholder, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.setBackground(CaseViews.box(this, AppTheme.field(this), 12, AppTheme.fieldStroke(this), 1));
        TextView t = CaseViews.text(this, placeholder, 14, AppTheme.sub(this), false);
        box.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(CaseViews.icon(this, R.drawable.ic_expand_more, AppTheme.text(this), 22));
        box.setOnClickListener(click);
        parent.addView(box, lpTop(0));
        return t;
    }

    private void setChosen(TextView t, String value, String placeholder) {
        boolean has = value != null && !value.isEmpty();
        t.setText(has ? value : placeholder);
        t.setTextColor(has ? AppTheme.text(this) : AppTheme.sub(this));
    }

    // ---------- header / progress ----------

    private LinearLayout header() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setPadding(dp(8), dp(10), dp(16), dp(12));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = CaseViews.icon(this, R.drawable.ic_arrow_back, AppTheme.text(this), 24);
        back.setPadding(dp(8), dp(8), dp(8), dp(8));
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView t = CaseViews.text(this, "New Case", 19, AppTheme.text(this), true);
        t.setPadding(dp(8), 0, 0, 0);
        top.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout prev = new LinearLayout(this);
        prev.setOrientation(LinearLayout.HORIZONTAL);
        prev.setGravity(Gravity.CENTER_VERTICAL);
        prev.setPadding(dp(16), dp(10), dp(18), dp(10));
        prev.setBackground(CaseViews.box(this, 0x00000000, 24, AppTheme.accent(this), 1));
        prev.addView(CaseViews.icon(this, R.drawable.ic_eye, AppTheme.accent(this), 20));
        TextView pt = CaseViews.text(this, "Preview", 14, AppTheme.accent(this), true);
        pt.setPadding(dp(8), 0, 0, 0);
        prev.addView(pt);
        prev.setOnClickListener(v -> preview());
        top.addView(prev);
        h.addView(top);

        progressText = CaseViews.text(this, "", 12, AppTheme.sub(this), false);
        progressText.setPadding(dp(8), dp(10), 0, dp(8));
        h.addView(progressText);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(CaseViews.box(this, AppTheme.cardStroke(this), 3, 0, 0));
        progFill = new View(this);
        progFill.setBackground(CaseViews.box(this, AppTheme.accent(this), 3, 0, 0));
        progRest = new View(this);
        bar.addView(progFill, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0f));
        bar.addView(progRest, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (float) REQUIRED));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(6));
        blp.leftMargin = dp(8);
        h.addView(bar, blp);
        return h;
    }

    private int doneCount() {
        int done = 0;
        if (!LegalUi.str(titleEdit).isEmpty()) done++;
        if (!LegalUi.str(regEdit).isEmpty()) done++;
        if (!courtName.isEmpty()) done++;
        if (!caseType.isEmpty()) done++;
        if (!LegalUi.str(caseNoEdit).isEmpty()) done++;
        return done;
    }

    private void refresh() {
        if (progressText == null || remainText == null || titleEdit == null
                || regEdit == null || caseNoEdit == null) return;
        int done = doneCount();
        progressText.setText(done + " of " + REQUIRED + " required fields complete");
        ((LinearLayout.LayoutParams) progFill.getLayoutParams()).weight = done;
        ((LinearLayout.LayoutParams) progRest.getLayoutParams()).weight = REQUIRED - done;
        progFill.requestLayout();
        progRest.requestLayout();
        int left = REQUIRED - done;
        remainText.setText(left == 0 ? "All required fields complete"
                : left + " required field" + (left == 1 ? "" : "s") + " remaining");
    }

    // ---------- court + mode ----------

    private LinearLayout toggleBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(6), dp(6), dp(6), dp(6));
        bar.setBackground(CaseViews.box(this, AppTheme.card(this), 18, AppTheme.cardStroke(this), 1));
        segAuto = segment(R.drawable.ic_search, "Auto-fill from court");
        segManual = segment(R.drawable.ic_edit, "Enter manually");
        segAuto.setOnClickListener(v -> { autoMode = true; styleToggle(); });
        segManual.setOnClickListener(v -> { autoMode = false; styleToggle(); });
        bar.addView(segAuto, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(segManual, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return bar;
    }

    private LinearLayout segment(int icon, String text) {
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.HORIZONTAL);
        s.setGravity(Gravity.CENTER);
        s.setPadding(dp(8), dp(14), dp(8), dp(14));
        s.addView(CaseViews.icon(this, icon, AppTheme.text(this), 20));
        TextView t = CaseViews.text(this, text, 13, AppTheme.text(this), false);
        t.setPadding(dp(8), 0, 0, 0);
        s.addView(t);
        return s;
    }

    private void styleToggle() {
        segAuto.setBackground(CaseViews.box(this, autoMode ? AppTheme.field(this) : 0x00000000, 14,
                autoMode ? AppTheme.fieldStroke(this) : 0, autoMode ? 1 : 0));
        segManual.setBackground(CaseViews.box(this, autoMode ? 0x00000000 : AppTheme.field(this), 14,
                autoMode ? 0 : AppTheme.fieldStroke(this), autoMode ? 0 : 1));
        if (cnrCard != null) cnrCard.setVisibility(autoMode ? View.VISIBLE : View.GONE);
    }

    private LinearLayout buildCnrCard() {
        LinearLayout c = card();
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(tile(R.drawable.ic_search));
        LinearLayout t = col(courtKind + " CNR Lookup", 15, "Auto-fill case details from CNR number");
        t.setPadding(dp(14), 0, 0, 0);
        cnrTitle = (TextView) t.getChildAt(0);
        head.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        c.addView(head);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        cnrLookup = new EditText(this);
        cnrLookup.setHint("Enter CNR Number");
        cnrLookup.setInputType(CAPS);
        cnrLookup.setSingleLine(true);
        cnrLookup.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16)});
        cnrLookup.setTextSize(14);
        cnrLookup.setTextColor(AppTheme.text(this));
        cnrLookup.setHintTextColor(AppTheme.sub(this));
        cnrLookup.setBackground(CaseViews.box(this, AppTheme.field(this), 28, AppTheme.fieldStroke(this), 1));
        cnrLookup.setPadding(dp(20), dp(14), dp(20), dp(14));
        row.addView(cnrLookup, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout go = new LinearLayout(this);
        go.setGravity(Gravity.CENTER);
        go.setBackground(CaseViews.box(this, 0xFF3B82F6, 28, 0, 0));
        go.addView(CaseViews.icon(this, R.drawable.ic_search, 0xFFFFFFFF, 24));
        go.setContentDescription("Look up CNR");
        go.setOnClickListener(v -> lookupCnr());
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(56), dp(56));
        glp.leftMargin = dp(10);
        row.addView(go, glp);
        c.addView(row, lpTop(14));
        return c;
    }

    private void lookupCnr() {
        String cnr = LegalUi.str(cnrLookup).toUpperCase(Locale.ROOT).replace(" ", "");
        if (!cnr.matches("[A-Z]{4}[0-9]{12}")) {
            Toast.makeText(this, "Enter a valid 16-character CNR, e.g. DLCT010012342023",
                    Toast.LENGTH_LONG).show();
            return;
        }
        cnrId.setText(cnr);
        new AlertDialog.Builder(this)
                .setTitle("CNR saved")
                .setMessage("Case details can't be fetched automatically: the eCourts service needs a "
                        + "captcha and has no open API for apps. This CNR will be kept with the case, "
                        + "so please fill in the other details.")
                .setPositiveButton("Enter manually", (d, w) -> { autoMode = false; styleToggle(); })
                .setNegativeButton("Close", null)
                .show();
    }

    // ---------- client ----------

    private LinearLayout buildClientCard() {
        LinearLayout c = card();
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(CaseViews.text(this, "Client", 15, AppTheme.text(this), false),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(CaseViews.text(this, "Optional", 12, AppTheme.sub(this), false));
        c.addView(head);
        clientBox = new LinearLayout(this);
        clientBox.setOrientation(LinearLayout.VERTICAL);
        c.addView(clientBox, lpTop(12));
        return c;
    }

    private void renderClient() {
        clientBox.removeAllViews();
        LegalStore.Client cl = clientId.isEmpty() ? null : legal.client(clientId);
        if (cl == null) clientId = "";

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setBackground(CaseViews.box(this, AppTheme.field(this), 14, AppTheme.fieldStroke(this), 1));
        row.addView(CaseViews.icon(this, cl == null ? R.drawable.ic_person_add : R.drawable.ic_person,
                AppTheme.accent(this), 24));
        LinearLayout t = col(cl == null ? "Select Client" : cl.name, 14,
                cl == null ? "Search your diary or create a client" : cl.mobile);
        t.setPadding(dp(12), 0, 0, 0);
        row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(CaseViews.icon(this, R.drawable.ic_chevron_right, AppTheme.text(this), 24));
        row.setOnClickListener(v -> pickClient());

        if (cl == null) {
            clientBox.addView(row);
        } else {
            LinearLayout wrap = new LinearLayout(this);
            wrap.setOrientation(LinearLayout.HORIZONTAL);
            wrap.setGravity(Gravity.CENTER_VERTICAL);
            wrap.addView(row, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            ImageView x = CaseViews.icon(this, R.drawable.ic_close, AppTheme.text(this), 22);
            x.setPadding(dp(10), dp(10), dp(10), dp(10));
            x.setContentDescription("Remove client");
            x.setOnClickListener(v -> { clientId = ""; renderClient(); });
            wrap.addView(x, new LinearLayout.LayoutParams(dp(48), dp(48)));
            clientBox.addView(wrap);
        }
        if (scaled) shrink(clientBox);
    }

    private void pickClient() {
        List<LegalStore.Client> all = legal.clients();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), 0);

        EditText search = new EditText(this);
        search.setHint("Search clients...");
        search.setSingleLine(true);
        box.addView(search);

        List<LegalStore.Client> shown = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows);
        ListView lv = new ListView(this);
        lv.setAdapter(ad);
        box.addView(lv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(280)));

        Runnable fill = () -> {
            String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            shown.clear();
            rows.clear();
            for (LegalStore.Client c : all) {
                if (q.isEmpty() || c.name.toLowerCase(Locale.ROOT).contains(q) || c.mobile.contains(q)) {
                    shown.add(c);
                    rows.add(c.name + "\n" + c.mobile);
                }
            }
            ad.notifyDataSetChanged();
        };
        fill.run();
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { fill.run(); }
        });

        android.widget.Button create = LegalUi.outlineButton(this, "+ Create New Client");
        box.addView(create, lpTop(10));

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Select Client")
                .setView(box)
                .setNegativeButton("Close", null)
                .create();
        lv.setOnItemClickListener((p, v, pos, id) -> {
            clientId = shown.get(pos).id;
            renderClient();
            dlg.dismiss();
        });
        create.setOnClickListener(v -> {
            dlg.dismiss();
            startActivityForResult(new Intent(this, AddClientActivity.class), REQ_CLIENT);
        });
        dlg.show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_CLIENT && res == RESULT_OK && data != null) {
            String id = data.getStringExtra("client_id");
            if (id != null) {
                clientId = id;
                renderClient();
            }
        }
        if (req == REQ_FILE && res == RESULT_OK && data != null) {
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) addPicked(clip.getItemAt(i).getUri());
            } else {
                addPicked(data.getData());
            }
            renderFiles();
        }
    }

    // ---------- case details ----------

    private LinearLayout buildCaseCard() {
        LinearLayout c = card();
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(tile(R.drawable.ic_check_circle));
        LinearLayout t = col("Case details", 15, "Fields marked * are required.");
        t.setPadding(dp(14), 0, 0, 0);
        head.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        c.addView(head);

        LinearLayout f = LegalUi.form(this);
        f.setPadding(0, 0, 0, 0);
        titleEdit = LegalUi.input(this, f, "Case Title*", "", TEXT);
        titleEdit.setHint("Enter Case Title");
        titleEdit.setMinLines(2);
        regEdit = LegalUi.dmyInput(this, f, "Registration Date*", "");
        regEdit.setHint("Select Date");
        regEdit.setOnClickListener(v ->
                PickerDialogs.calendar(this, "Select Registration Date", regEdit));

        f.addView(lbl("Court"));
        kindText = dropdown(f, courtKind, v ->
                PickerDialogs.courtSheet(this, COURT_KINDS, COURT_ICONS, courtKind, s -> {
                    courtKind = s;
                    setChosen(kindText, s, courtKind);
                    cnrTitle.setText(s + " CNR Lookup");
                }));
        setChosen(kindText, courtKind, courtKind);

        f.addView(lbl("Court name / type *"));
        courtNameText = dropdown(f, "Select Court Type", v ->
                PickerDialogs.courtTypes(this, courtTypes(), courtName, this::addCourtDialog, s -> {
                    courtName = s;
                    setChosen(courtNameText, s, "Select Court Type");
                    refresh();
                }));
        f.addView(lbl("Case type*"));
        caseTypeText = dropdown(f, "Select Particular", v ->
                choose("Case type", Arrays.asList(DiaryStore.CASE_TYPES), s -> {
                    caseType = s;
                    setChosen(caseTypeText, s, "Select Particular");
                    refresh();
                }));
        caseNoEdit = LegalUi.input(this, f, "Case No.*", "", TEXT);
        caseNoEdit.setHint("Case No.");
        c.addView(f, lpTop(4));

        titleEdit.addTextChangedListener(watcher);
        regEdit.addTextChangedListener(watcher);
        caseNoEdit.addTextChangedListener(watcher);
        return c;
    }

    /** Built-in court types first, then the ones already saved in the app (no repeats). */
    private List<String> courtTypes() {
        java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>(Arrays.asList(DEFAULT_COURT_TYPES));
        all.addAll(store.courts());
        return new ArrayList<>(all);
    }

    private void addCourtDialog() {
        PickerDialogs.customCourt(this, s -> {
            boolean known = false;
            for (String t : courtTypes()) if (t.equalsIgnoreCase(s)) { s = t; known = true; break; }
            if (!known) {
                List<String> l = store.courts();
                l.add(s);
                store.saveCourts(l);
            }
            courtName = s;
            setChosen(courtNameText, s, "Select Court Type");
            refresh();
        });
    }

    // ---------- additional details ----------

    private LinearLayout buildAdditionalCard() {
        LinearLayout c = card();
        c.setPadding(0, 0, 0, 0);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout t = col("Additional details", 15, "Parties, fees, notes, FIR and acts");
        head.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ImageView toggle = CaseViews.icon(this, R.drawable.ic_add_circle, AppTheme.accent(this), 30);
        head.addView(toggle);
        c.addView(head);

        LinearLayout extra = new LinearLayout(this);
        extra.setOrientation(LinearLayout.VERTICAL);
        extra.setVisibility(View.GONE);
        c.addView(extra);
        head.setOnClickListener(v -> {
            boolean open = extra.getVisibility() != View.VISIBLE;
            extra.setVisibility(open ? View.VISIBLE : View.GONE);
            toggle.setImageResource(open ? R.drawable.ic_remove_circle : R.drawable.ic_add_circle);
        });

        // case identifiers
        LinearLayout f1 = subForm();
        filingEdit = LegalUi.text(this, f1, "Filing number", "");
        diaryEdit = LegalUi.text(this, f1, "Diary number", "");
        cnrId = LegalUi.input(this, f1, "CNR number", "", CAPS);
        extra.addView(section(R.drawable.ic_tag, "Case identifiers",
                "Filing number, diary number and CNR", f1));

        // parties
        LinearLayout f2 = subForm();
        petEdit = LegalUi.input(this, f2, "Petitioner / Plaintiff", "", NAME);
        resEdit = LegalUi.input(this, f2, "Respondent / Defendant", "", NAME);
        appearEdit = LegalUi.input(this, f2, "Appearing for", "", NAME);
        extra.addView(section(R.drawable.ic_contacts, "Parties & representation",
                "Petitioner, respondent and appearing for", f2));

        // fee
        LinearLayout f3 = subForm();
        feeEdit = LegalUi.input(this, f3, "Agreed fee", "", InputType.TYPE_CLASS_NUMBER);
        feeNotesEdit = LegalUi.text(this, f3, "Payment notes", "");
        extra.addView(section(R.drawable.ic_card, "Agreed fee",
                "Record the fee and payment notes", f3));

        // notes & other
        LinearLayout f4 = subForm();
        judgeEdit = LegalUi.input(this, f4, "Judge", "", NAME);
        priority = LegalUi.spinner(this, f4, "Priority", PRIORITIES, PRIORITIES[0]);
        subTypeEdit = LegalUi.text(this, f4, "Case sub type", "");
        notesEdit = LegalUi.multiline(this, f4, "Notes", "");
        notesEdit.setMinLines(3);
        extra.addView(section(R.drawable.ic_edit, "Notes & other details",
                "Judge, priority, notes and sub type", f4));

        // FIR
        LinearLayout f5 = subForm();
        stationEdit = LegalUi.text(this, f5, "Police station", "");
        firNoEdit = LegalUi.text(this, f5, "FIR number", "");
        firDateEdit = LegalUi.dmyInput(this, f5, "FIR date", "");
        firDateEdit.setHint("Select Date");
        firDateEdit.setOnClickListener(v -> PickerDialogs.calendar(this, "Select FIR Date", firDateEdit));
        extra.addView(section(R.drawable.ic_notes, "FIR Details",
                "Police station, FIR number and date", f5));

        // acts and sections
        LinearLayout f6 = subForm();
        actsBox = new LinearLayout(this);
        actsBox.setOrientation(LinearLayout.VERTICAL);
        f6.addView(actsBox);
        android.widget.Button add = LegalUi.outlineButton(this, "+ Add act");
        add.setOnClickListener(v -> addActRow());
        f6.addView(add, lpTop(10));
        extra.addView(section(R.drawable.ic_book, "Acts and Sections",
                "Add applicable acts and sections", f6));
        return c;
    }

    // ---------- upload file ----------

    private LinearLayout buildUploadCard() {
        LinearLayout c = card();
        c.setPadding(0, 0, 0, 0);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout t = col("Upload File", 15, "Upload case file here");
        head.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ImageView plus = CaseViews.icon(this, R.drawable.ic_add_circle, AppTheme.accent(this), 30);
        plus.setContentDescription("Choose a PDF or image");
        head.addView(plus);
        head.setOnClickListener(v -> pickFile());
        c.addView(head);

        filesBox = new LinearLayout(this);
        filesBox.setOrientation(LinearLayout.VERTICAL);
        filesBox.setPadding(dp(16), 0, dp(16), 0);
        c.addView(filesBox);
        return c;
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf", "image/*"});
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_FILE);
        } catch (Exception e) {
            Toast.makeText(this, "No file picker available on this device", Toast.LENGTH_LONG).show();
        }
    }

    private void addPicked(Uri uri) {
        if (uri == null || pickedUris.contains(uri)) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // not every provider offers a persistable grant; the file still shows in the list
        }
        pickedUris.add(uri);
        pickedNames.add(displayName(uri));
    }

    private String displayName(Uri uri) {
        String name = null;
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int col = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (col >= 0) name = c.getString(col);
            }
        } catch (Exception ignored) {
            // fall through to the last path segment
        }
        if (name == null || name.isEmpty()) name = uri.getLastPathSegment();
        return name == null ? "File" : name;
    }

    private void renderFiles() {
        filesBox.removeAllViews();
        for (int i = 0; i < pickedUris.size(); i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(4), dp(8));
            row.setBackground(CaseViews.box(this, AppTheme.field(this), 12, AppTheme.fieldStroke(this), 1));
            boolean pdf = pickedNames.get(i).toLowerCase(Locale.ROOT).endsWith(".pdf");
            row.addView(CaseViews.icon(this, pdf ? R.drawable.ic_pdf : R.drawable.ic_notes,
                    AppTheme.accent(this), 22));
            TextView n = CaseViews.text(this, pickedNames.get(i), 13, AppTheme.text(this), false);
            n.setSingleLine(true);
            n.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            n.setPadding(dp(10), 0, 0, 0);
            row.addView(n, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            ImageView x = CaseViews.icon(this, R.drawable.ic_close, AppTheme.sub(this), 20);
            x.setPadding(dp(10), dp(10), dp(10), dp(10));
            x.setContentDescription("Remove file");
            x.setOnClickListener(v -> {
                pickedUris.remove(idx);
                pickedNames.remove(idx);
                renderFiles();
            });
            row.addView(x, new LinearLayout.LayoutParams(dp(40), dp(40)));
            LinearLayout.LayoutParams lp = lpTop(0);
            lp.bottomMargin = dp(8);
            filesBox.addView(row, lp);
        }
        filesBox.setPadding(dp(16), 0, dp(16), pickedUris.isEmpty() ? 0 : dp(8));
        if (scaled) shrink(filesBox);
    }

    private LinearLayout subForm() {
        LinearLayout f = LegalUi.form(this);
        f.setPadding(dp(16), 0, dp(16), dp(14));
        return f;
    }

    private LinearLayout section(int icon, String title, String sub, View content) {
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);

        View line = new View(this);
        line.setBackgroundColor(AppTheme.cardStroke(this));
        s.addView(line, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(14), dp(16), dp(14));
        head.addView(tile(icon));
        LinearLayout t = col(title, 14, sub);
        t.setPadding(dp(14), 0, 0, 0);
        head.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ImageView chev = CaseViews.icon(this, R.drawable.ic_expand_more, AppTheme.text(this), 24);
        head.addView(chev);
        s.addView(head);

        content.setVisibility(View.GONE);
        s.addView(content);
        head.setOnClickListener(v -> {
            boolean open = content.getVisibility() != View.VISIBLE;
            content.setVisibility(open ? View.VISIBLE : View.GONE);
            chev.setRotation(open ? 180f : 0f);
        });
        return s;
    }

    private void addActRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        EditText a = new EditText(this);
        a.setHint("Act (e.g. IPC)");
        a.setSingleLine(true);
        a.setTextSize(14);
        EditText s = new EditText(this);
        s.setHint("Sections");
        s.setSingleLine(true);
        s.setTextSize(14);
        ImageView x = CaseViews.icon(this, R.drawable.ic_close, AppTheme.sub(this), 20);
        x.setPadding(dp(10), dp(10), dp(10), dp(10));
        x.setContentDescription("Remove act");
        EditText[] pair = {a, s};
        actRows.add(pair);
        x.setOnClickListener(v -> {
            actsBox.removeView(row);
            actRows.remove(pair);
        });
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp1.rightMargin = dp(8);
        row.addView(a, lp1);
        row.addView(s, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(x, new LinearLayout.LayoutParams(dp(40), dp(40)));
        if (scaled) shrink(row);
        actsBox.addView(row);
    }

    private String actsText() {
        StringBuilder sb = new StringBuilder();
        for (EditText[] p : actRows) {
            String a = LegalUi.str(p[0]);
            String s = LegalUi.str(p[1]);
            if (a.isEmpty() && s.isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(a);
            if (!s.isEmpty()) sb.append(a.isEmpty() ? "" : " \u2013 ").append(s);
        }
        return sb.toString();
    }

    // ---------- bottom bar, preview, create ----------

    private LinearLayout bottomBar() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(16), dp(10), dp(16), dp(16));
        b.setBackgroundColor(AppTheme.card(this));
        remainText = CaseViews.text(this, "", 12, AppTheme.text(this), false);
        remainText.setGravity(Gravity.CENTER);
        b.addView(remainText);

        TextView create = CaseViews.text(this, "+  Create Case", 15, DARK, true);
        create.setGravity(Gravity.CENTER);
        create.setPadding(dp(16), dp(13), dp(16), dp(13));
        create.setBackground(CaseViews.box(this, YELLOW, 30, 0, 0));
        create.setOnClickListener(v -> create());
        b.addView(create, lpTop(10));
        return b;
    }

    private List<String> missing() {
        List<String> m = new ArrayList<>();
        if (LegalUi.str(titleEdit).isEmpty()) m.add("Case Title");
        if (LegalUi.str(regEdit).isEmpty()) m.add("Registration Date");
        if (courtName.isEmpty()) m.add("Court name / type");
        if (caseType.isEmpty()) m.add("Case type");
        if (LegalUi.str(caseNoEdit).isEmpty()) m.add("Case No.");
        return m;
    }

    private void preview() {
        LegalStore.Client cl = clientId.isEmpty() ? null : legal.client(clientId);
        StringBuilder sb = new StringBuilder();
        line(sb, "Court", courtKind);
        line(sb, "Client", cl == null ? "" : cl.name);
        line(sb, "Case title", LegalUi.str(titleEdit));
        line(sb, "Registration date", LegalUi.str(regEdit));
        line(sb, "Court name / type", courtName);
        line(sb, "Case type", caseType);
        line(sb, "Case no.", LegalUi.str(caseNoEdit));
        line(sb, "CNR", cnrValue());
        line(sb, "Filing no.", LegalUi.str(filingEdit));
        line(sb, "Diary no.", LegalUi.str(diaryEdit));
        line(sb, "Petitioner", LegalUi.str(petEdit));
        line(sb, "Respondent", LegalUi.str(resEdit));
        line(sb, "Appearing for", LegalUi.str(appearEdit));
        line(sb, "Agreed fee", LegalUi.str(feeEdit));
        line(sb, "Judge", LegalUi.str(judgeEdit));
        line(sb, "FIR", (LegalUi.str(stationEdit) + " " + LegalUi.str(firNoEdit) + " "
                + LegalUi.str(firDateEdit)).trim());
        line(sb, "Acts", actsText().replace('\n', ';'));
        new AlertDialog.Builder(this).setTitle("Preview")
                .setMessage(sb.toString())
                .setPositiveButton("Close", null).show();
    }

    private static void line(StringBuilder sb, String k, String v) {
        if (v == null || v.isEmpty()) return;
        sb.append(k).append(": ").append(v).append('\n');
    }

    private String cnrValue() {
        String v = LegalUi.str(cnrId).toUpperCase(Locale.ROOT);
        if (v.isEmpty()) v = LegalUi.str(cnrLookup).toUpperCase(Locale.ROOT);
        return v;
    }

    private void create() {
        List<String> m = missing();
        if (!m.isEmpty()) {
            Toast.makeText(this, "Required: " + android.text.TextUtils.join(", ", m),
                    Toast.LENGTH_LONG).show();
            return;
        }
        LegalStore.Client cl = clientId.isEmpty() ? null : legal.client(clientId);

        DiaryStore.HCase c = new DiaryStore.HCase();
        c.caseTitle = LegalUi.str(titleEdit);
        c.regDate = LegalUi.str(regEdit);
        c.courtKind = courtKind;
        c.court = courtName;
        c.type = caseType;
        c.number = LegalUi.str(caseNoEdit);
        c.clientId = clientId;

        String pet = LegalUi.str(petEdit);
        c.first = pet.isEmpty() ? c.caseTitle : pet;
        c.second = LegalUi.str(resEdit);
        String appear = LegalUi.str(appearEdit);
        c.representing = !appear.isEmpty() ? appear : (cl != null ? cl.name : "");

        c.cnr = cnrValue();
        c.filingNo = LegalUi.str(filingEdit);
        c.diaryNo = LegalUi.str(diaryEdit);
        c.billTotal = LegalUi.num(feeEdit);
        c.feeNotes = LegalUi.str(feeNotesEdit);
        c.judge = LegalUi.str(judgeEdit);
        c.priority = String.valueOf(priority.getSelectedItem());
        c.subType = LegalUi.str(subTypeEdit);
        c.notes = LegalUi.str(notesEdit);
        c.firStation = LegalUi.str(stationEdit);
        c.firNo = LegalUi.str(firNoEdit);
        c.firDate = LegalUi.str(firDateEdit);
        c.acts = actsText();

        store.saveCase(c);
        Toast.makeText(this, "Case created", Toast.LENGTH_SHORT).show();
        finish();
    }
}
