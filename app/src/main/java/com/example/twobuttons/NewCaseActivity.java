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
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.provider.MediaStore;
import androidx.core.content.FileProvider;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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
    private static final int REQ_CAMERA = 53;
    private static final int REQUIRED = 6;
    /** Everything on this page is drawn a little smaller than the shared default sizes. */
    private static final float TEXT_SCALE = 0.9f;
    private static final String[] COURT_KINDS = {"District Court", "High Court", "Supreme Court",
            "Tribunal (NCLT / DRT / DRAT)", "Consumer Court", "Revenue Court"};
    private static final int[] COURT_ICONS = {R.drawable.ic_home, R.drawable.ic_briefcase,
            R.drawable.ic_shield, R.drawable.ic_award, R.drawable.ic_bag, R.drawable.ic_map};
    private static final String CHOOSE_COURT = "Choose a court";
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

    private String courtKind = "";
    private String courtName = "";
    private String caseType = "";
    private String clientId = "";
    private boolean autoMode = false;   // opens on "Enter manually"

    private TextView kindText, courtNameText, caseTypeText, cnrTitle, progressText, remainText;
    private LinearLayout cnrCard, clientBox, actsBox, segAuto, segManual;
    private View progFill, progRest;
    private EditText cnrLookup, titleEdit, caseNoEdit, regEdit;
    private EditText cnrId, filingEdit, diaryEdit, feeEdit, feeNotesEdit;
    private final List<EditText> petRows = new ArrayList<>();   // several petitioners
    private final List<EditText> resRows = new ArrayList<>();   // several respondents
    private final List<EditText> advRows = new ArrayList<>();   // several opposing advocates
    private StageStore stages;
    private String judgeName = "", curStatus = "", nextStatus = "", clientRole = "";
    private TextView judgeText, curStatusText, nextStatusText;
    private EditText roomEdit, nextHearingEdit;
    private EditText subTypeEdit, notesEdit, stationEdit, firNoEdit, firDateEdit;
    private Spinner priority;
    private final List<EditText[]> actRows = new ArrayList<>();
    private final List<EditText> firSecRows = new ArrayList<>();
    private LinearLayout firSecBox;
    private EditText firYearEdit;
    private final List<Uri> pickedUris = new ArrayList<>();
    private final List<String> pickedNames = new ArrayList<>();
    private int replaceIdx = -1;          // >= 0 while a file is being replaced
    private Uri cameraUri;
    // upload dialog state: files chosen inside the open dialog, and a callback that redraws its drop area
    private final List<Uri> dlgUris = new ArrayList<>();
    private Runnable dlgRefresh = null;
    private int dragCur = -1;             // row being dragged, -1 = none
    private float dragDownY, dragShift;
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
        stages = new StageStore(this);

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
        body.addView(buildUploadCard(), cardLp());      // 1st card
        body.addView(buildCaseCard(), cardLp());        // 2nd card
        cnrCard = buildCnrCard();
        body.addView(cnrCard, cardLp());
        body.addView(buildAdditionalCard(), cardLp());

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
        if (v instanceof TextView && !(v.getTag() instanceof View)) {   // dropdown text stays 15sp like the boxes
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
        PickerDialogs.optionSheet(this, title, null, opts, -1, i -> cb.accept(opts.get(i)));
    }

    /** Dropdown-looking row; the caller keeps the returned TextView to show the chosen value. */
    private TextView dropdown(LinearLayout parent, String placeholder, View.OnClickListener click) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(14), 0, dp(14), 0);
        box.setBackground(dropBg(false));
        box.setMinimumHeight(dp(RoundEdit.HEIGHT_DP));
        TextView t = CaseViews.text(this, placeholder, 15, AppTheme.sub(this), false);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setTag(box);
        box.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(CaseViews.icon(this, R.drawable.ic_expand_more, AppTheme.text(this), 22));
        box.setOnClickListener(click);
        LinearLayout.LayoutParams dl = lpTop(0);
        dl.height = dp(RoundEdit.HEIGHT_DP);          // exactly the text box height
        parent.addView(box, dl);
        return t;
    }

    /** Same round outline as RoundEdit: black normally, red when a mandatory pick is missing. */
    private android.graphics.drawable.GradientDrawable dropBg(boolean invalid) {
        return CaseViews.box(this, AppTheme.field(this), 14,
                invalid ? RoundEdit.RED : AppTheme.text(this), invalid ? 2 : 1);
    }

    private void dropInvalid(TextView t, boolean invalid) {
        if (t != null && t.getTag() instanceof View) ((View) t.getTag()).setBackground(dropBg(invalid));
    }

    private void setChosen(TextView t, String value, String placeholder) {
        boolean has = value != null && !value.isEmpty();
        if (has) dropInvalid(t, false);
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
        if (!clientRole.isEmpty()) done++;
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
        LinearLayout t = col(cnrHeading(), 15, "Auto-fill case details from CNR number");
        t.setPadding(dp(14), 0, 0, 0);
        cnrTitle = (TextView) t.getChildAt(0);
        head.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        c.addView(head);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        cnrLookup = new RoundEdit(this);
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

    /** Client picker. Sits inside Case details, right under "My client is". */
    private LinearLayout buildClientCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        c.setBackground(CaseViews.box(this, AppTheme.card(this), 16, AppTheme.cardStroke(this), 1));
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

    /**
     * Custom = typed in by hand. Clients picked from the phone's contacts and the sample client
     * the app ships with are not custom.
     */
    private static boolean isCustomClient(LegalStore.Client c) {
        boolean sample = "Mohammad Sajib".equals(c.name) && "CL-0008".equals(c.code);
        return !sample && !c.fromContacts;
    }

    private void pickClient() {
        final List<LegalStore.Client> all = new ArrayList<>(legal.clients());
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), 0);

        EditText search = new RoundEdit(this);
        search.setHint("Search clients...");
        search.setSingleLine(true);
        box.addView(search);

        final List<LegalStore.Client> shown = new ArrayList<>();
        final AlertDialog[] dlgRef = new AlertDialog[1];
        final Runnable[] fill = new Runnable[1];

        BaseAdapter ad = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }

            @Override
            public View getView(int i, View convert, ViewGroup parent) {
                final LegalStore.Client c = shown.get(i);
                LinearLayout r = new LinearLayout(NewCaseActivity.this);
                r.setOrientation(LinearLayout.HORIZONTAL);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setMinimumHeight(dp(54));
                r.setPadding(dp(4), dp(4), dp(0), dp(4));
                LinearLayout t = new LinearLayout(NewCaseActivity.this);
                t.setOrientation(LinearLayout.VERTICAL);
                t.addView(CaseViews.text(NewCaseActivity.this, c.name, 16, AppTheme.text(NewCaseActivity.this), false));
                t.addView(CaseViews.text(NewCaseActivity.this, c.mobile, 14, AppTheme.sub(NewCaseActivity.this), false));
                r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                if (isCustomClient(c)) {
                    TextView badge = CaseViews.text(NewCaseActivity.this, "Custom", 12,
                            AppTheme.isDark(NewCaseActivity.this) ? 0xFF121212 : 0xFFFFFFFF, true);
                    badge.setGravity(Gravity.CENTER);
                    badge.setPadding(dp(11), dp(4), dp(11), dp(4));
                    badge.setBackground(CaseViews.box(NewCaseActivity.this, AppTheme.accent(NewCaseActivity.this), 14, 0, 0));
                    r.addView(badge);
                }
                ImageView x = CaseViews.icon(NewCaseActivity.this, R.drawable.ic_delete, LegalUi.RED, 40);
                x.setPadding(dp(9), dp(9), dp(9), dp(9));
                x.setContentDescription("Delete " + c.name);
                x.setFocusable(false);
                x.setOnClickListener(v -> new AlertDialog.Builder(NewCaseActivity.this)
                        .setTitle("Delete client?")
                        .setMessage("\"" + c.name + "\" will be removed from your clients.")
                        .setPositiveButton("Delete", (dd, w) -> {
                            legal.deleteClient(c.id);
                            all.removeIf(o -> o.id.equals(c.id));
                            if (c.id.equals(clientId)) { clientId = ""; renderClient(); }
                            fill[0].run();
                        })
                        .setNegativeButton("Cancel", null)
                        .show());
                r.addView(x);
                return r;
            }
        };

        ListView lv = new ListView(this);
        lv.setAdapter(ad);
        box.addView(lv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(280)));

        fill[0] = () -> {
            String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            shown.clear();
            for (LegalStore.Client c : all) {
                if (q.isEmpty() || c.name.toLowerCase(Locale.ROOT).contains(q) || c.mobile.contains(q)) {
                    shown.add(c);
                }
            }
            ad.notifyDataSetChanged();
        };
        fill[0].run();
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { fill[0].run(); }
        });

        android.widget.Button create = LegalUi.outlineButton(this, "+ Create New Client");
        box.addView(create, lpTop(10));

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Select Client")
                .setView(box)
                .setNegativeButton("Close", null)
                .create();
        dlgRef[0] = dlg;
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
        if (dlgRefresh != null && replaceIdx < 0) {       // the upload dialog is open: collect there
            if (req == REQ_CAMERA && res == RESULT_OK && cameraUri != null) dlgAdd(cameraUri);
            if (req == REQ_FILE && res == RESULT_OK && data != null) {
                ClipData clip = data.getClipData();
                if (clip != null) {
                    for (int i = 0; i < clip.getItemCount(); i++) dlgAdd(clip.getItemAt(i).getUri());
                } else {
                    dlgAdd(data.getData());
                }
            }
            dlgRefresh.run();
            return;
        }
        if (req == REQ_CAMERA && res == RESULT_OK && cameraUri != null) {
            addPicked(cameraUri);
            renderFiles();
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
        f.addView(clientSideRow(), lpTop(10));   // "My client is": first option in Case details, mandatory
        f.addView(buildClientCard(), lpTop(16));
        f.addView(lbl("Current Stage"));
        curStatusText = dropdown(f, "Select Current Stage", v -> openStages(true));
        titleEdit = LegalUi.input(this, f, "Case Title*", "", TEXT);
        ((RoundEdit) titleEdit).setRequired(true);
        titleEdit.setHint("Enter Case Title");
        regEdit = LegalUi.dmyInput(this, f, "Registration Date*", "");
        regEdit.setHint("Select Date");
        regEdit.setOnClickListener(v ->
                PickerDialogs.calendar(this, "Select Registration Date", regEdit));

        f.addView(lbl("Court"));
        kindText = dropdown(f, CHOOSE_COURT, v ->
                PickerDialogs.courtSheet(this, COURT_KINDS, COURT_ICONS, courtKind, s -> {
                    courtKind = s;
                    setChosen(kindText, s, CHOOSE_COURT);
                    cnrTitle.setText(cnrHeading());
                }));
        setChosen(kindText, courtKind, CHOOSE_COURT);

        f.addView(lbl("Court name / type *"));
        courtNameText = dropdown(f, "Select Court Type", v ->
                PickerDialogs.courtTypes(this, this::courtTypes, courtName, this::addCourtDialog,
                        this::isCustomCourt, this::deleteCourtType, s -> {
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
        ((RoundEdit) caseNoEdit).setRequired(true);
        caseNoEdit.setHint("Case No.");
        nextHearingEdit = LegalUi.dmyInput(this, f, "Next hearing date", "");
        nextHearingEdit.setHint("Select Date");
        nextHearingEdit.setOnClickListener(v ->
                PickerDialogs.calendar(this, "Select Next Hearing Date", nextHearingEdit));
        f.addView(lbl("Next status"));
        nextStatusText = dropdown(f, "Select Status", v -> openStages(false));
        c.addView(f, lpTop(4));

        titleEdit.addTextChangedListener(watcher);
        regEdit.addTextChangedListener(watcher);
        caseNoEdit.addTextChangedListener(watcher);
        return c;
    }

    private String cnrHeading() {
        return courtKind.isEmpty() ? "Case Search & Auto-Fill" : courtKind + " CNR Lookup";
    }

    private static boolean isDefaultCourt(String name) {
        for (String t : DEFAULT_COURT_TYPES) if (t.equalsIgnoreCase(name)) return true;
        return false;
    }

    private boolean isCustomCourt(String name) {
        return !isDefaultCourt(name);
    }

    /** Court types the user added (newest first), then the built-in ones. No repeats. */
    private List<String> courtTypes() {
        List<String> saved = store.courts();
        java.util.Collections.reverse(saved);
        java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>();
        for (String s : saved) if (isCustomCourt(s)) all.add(s);
        all.addAll(Arrays.asList(DEFAULT_COURT_TYPES));
        return new ArrayList<>(all);
    }

    private void deleteCourtType(String name) {
        List<String> l = store.courts();
        l.removeIf(x -> x.equalsIgnoreCase(name));
        store.saveCourts(l);
        if (name.equalsIgnoreCase(courtName)) {
            courtName = "";
            setChosen(courtNameText, "", "Select Court Type");
            refresh();
        }
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
        multiNames(f2, "Petitioner / Plaintiff / First Party", "Add petitioner",
                "Enter Petitioner / First Party", petRows);
        multiNames(f2, "Respondent / Defendant / Opposite Party", "Add respondent",
                "Enter Respondent / Opposite Party", resRows);
        multiNames(f2, "Opposing Advocate", "Add opposing advocate",
                "Enter Opposing Advocate Name", advRows);
        extra.addView(section(R.drawable.ic_contacts, "Parties & representation",
                "Petitioners, respondents and opposing advocates", f2));

        // fee
        LinearLayout f3 = subForm();
        feeEdit = LegalUi.input(this, f3, "Agreed fee", "", InputType.TYPE_CLASS_NUMBER);
        feeNotesEdit = LegalUi.text(this, f3, "Payment notes", "");
        extra.addView(section(R.drawable.ic_card, "Agreed fee",
                "Record the fee and payment notes", f3));

        // notes & other
        LinearLayout fj = subForm();
        fj.addView(lbl("Judge"));
        judgeText = dropdown(fj, "Select Judge", v ->
                PickerDialogs.judges(this, this::judgeNames, judgeName, this::addJudgeDialog,
                        n -> true, this::deleteJudge, this::pickJudge));
        roomEdit = LegalUi.multiline(this, fj, "Court room / address", "");
        roomEdit.setMinLines(2);
        roomEdit.setHint("Court room no. and address");
        extra.addView(section(R.drawable.ic_court, "Judge & hearing",
                "Judge and court room / address", fj));

        LinearLayout f4 = subForm();
        priority = LegalUi.spinner(this, f4, "Priority", PRIORITIES, PRIORITIES[0]);
        subTypeEdit = LegalUi.text(this, f4, "Case sub type", "");
        notesEdit = LegalUi.multiline(this, f4, "Notes", "");
        notesEdit.setMinLines(3);
        extra.addView(section(R.drawable.ic_edit, "Notes & other details",
                "Priority, notes and sub type", f4));

        // FIR
        LinearLayout f5 = subForm();
        stationEdit = LegalUi.text(this, f5, "Police station", "");
        firNoEdit = LegalUi.text(this, f5, "FIR number", "");
        firYearEdit = LegalUi.input(this, f5, "FIR year", "", InputType.TYPE_CLASS_NUMBER);
        firYearEdit.setHint("e.g. 2024");
        firYearEdit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
        TextView secLabel = CaseViews.text(this, "Under section(s)", 12, AppTheme.sub(this), true);
        secLabel.setPadding(0, dp(10), 0, 0);
        f5.addView(secLabel);
        firSecBox = new LinearLayout(this);
        firSecBox.setOrientation(LinearLayout.VERTICAL);
        f5.addView(firSecBox);
        android.widget.Button addSec = LegalUi.outlineButton(this, "+ Add section");
        addSec.setOnClickListener(v -> addFirSectionRow());
        f5.addView(addSec, lpTop(10));
        addFirSectionRow();
        firDateEdit = LegalUi.dmyInput(this, f5, "FIR date", "");
        firDateEdit.setHint("Select Date");
        firDateEdit.setOnClickListener(v -> PickerDialogs.calendar(this, "Select FIR Date", firDateEdit));
        extra.addView(section(R.drawable.ic_notes, "FIR Details",
                "Police station, FIR number, year, sections and date", f5));

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
        LinearLayout t = col("Upload Legal Document/File", 15, "Upload case file here");
        head.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ImageView plus = CaseViews.icon(this, R.drawable.ic_add_circle, AppTheme.accent(this), 30);
        plus.setContentDescription("Upload legal document or file");
        head.addView(plus);
        head.setOnClickListener(v -> uploadDialog());
        c.addView(head);

        filesBox = new LinearLayout(this);
        filesBox.setOrientation(LinearLayout.VERTICAL);
        filesBox.setPadding(dp(16), 0, dp(16), 0);
        c.addView(filesBox);
        return c;
    }

    // ---------- upload legal document dialog ----------

    private void dlgAdd(Uri uri) {
        if (uri == null || dlgUris.contains(uri)) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // not every provider offers a persistable grant
        }
        dlgUris.add(uri);
    }

    private long fileSize(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int col = c.getColumnIndex(OpenableColumns.SIZE);
                if (col >= 0 && !c.isNull(col)) return c.getLong(col);
            }
        } catch (Exception ignored) {
            // size stays unknown
        }
        return 0;
    }

    private static String sizeText(long b) {
        if (b <= 0) return "";
        if (b < 1024 * 1024) return String.format(Locale.US, "%.1f KB", b / 1024.0);
        return String.format(Locale.US, "%.1f MB", b / (1024.0 * 1024.0));
    }

    private static String extOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot >= name.length() - 6 ? name.substring(dot) : "";
    }

    /** Document name + category + matter, then "Tap to choose" (upload PDF / Word / image, or take a photo). */
    private void uploadDialog() {
        dlgUris.clear();
        replaceIdx = -1;
        LinearLayout f = LegalUi.form(this);
        EditText name = LegalUi.text(this, f, "Document Name / Caption *", "");
        name.setHint("e.g. Interim Injunction Application Under Order 39");
        Spinner cat = LegalUi.spinner(this, f, "Document Category", LegalStore.DOC_CATEGORIES, "Other");

        List<String> matters = new ArrayList<>();
        matters.add("-- General Document Vault --");
        for (CaseEntry ce : new CaseStore(this).load()) matters.add(ce.title);
        Spinner matter = LegalUi.spinner(this, f, "Attach to Matter (Optional)",
                matters.toArray(new String[0]), matters.get(0));

        // dashed "tap to choose" area
        final LinearLayout drop = new LinearLayout(this);
        drop.setOrientation(LinearLayout.VERTICAL);
        drop.setGravity(Gravity.CENTER);
        drop.setPadding(dp(16), dp(22), dp(16), dp(22));
        android.graphics.drawable.GradientDrawable dash = new android.graphics.drawable.GradientDrawable();
        dash.setColor(AppTheme.field(this));
        dash.setCornerRadius(dp(14));
        dash.setStroke(dp(2), AppTheme.accent(this), dp(6), dp(4));
        drop.setBackground(dash);
        LinearLayout.LayoutParams dlp = lpTop(16);
        f.addView(drop, dlp);

        final Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            drop.removeAllViews();
            drop.addView(CaseViews.icon(this, dlgUris.isEmpty() ? R.drawable.ic_add_circle : R.drawable.ic_pdf,
                    AppTheme.accent(this), 34));
            if (dlgUris.isEmpty()) {
                TextView a = CaseViews.text(this, "Tap to choose PDF, Word or image file", 14, AppTheme.accent(this), true);
                a.setGravity(Gravity.CENTER);
                a.setPadding(0, dp(8), 0, 0);
                drop.addView(a);
                TextView b = CaseViews.text(this, "Supports .pdf, .doc, .docx, .jpg, .png files, or take a photo",
                        12, AppTheme.sub(this), false);
                b.setGravity(Gravity.CENTER);
                drop.addView(b);
            } else {
                String first = displayName(dlgUris.get(0));
                TextView a = CaseViews.text(this, dlgUris.size() == 1 ? first
                        : first + "  +" + (dlgUris.size() - 1) + " more", 14, AppTheme.text(this), true);
                a.setGravity(Gravity.CENTER);
                a.setSingleLine(true);
                a.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                a.setPadding(0, dp(8), 0, 0);
                drop.addView(a);
                TextView b = CaseViews.text(this, "Tap to change", 12, AppTheme.accent(this), true);
                drop.addView(b);
            }
        };
        draw[0].run();
        dlgRefresh = draw[0];
        drop.setOnClickListener(v -> {
            if (!dlgUris.isEmpty()) dlgUris.clear();   // choosing again replaces what was picked in this dialog
            PickerDialogs.actions(this, "Upload file",
                    new String[]{"Upload PDF / Word / image", "Take photo"},
                    new int[]{R.drawable.ic_pdf, R.drawable.ic_camera},
                    i -> {
                        if (i == 0) pickFile();
                        else takePhoto();
                    });
        });

        LegalUi.formDialog(this, "Upload Legal Document", f, () -> {
            if (!LegalUi.need(this, name, "Document name")) return false;
            if (dlgUris.isEmpty()) {
                Toast.makeText(this, "Choose a file first", Toast.LENGTH_SHORT).show();
                return false;
            }
            String base = LegalUi.str(name);
            String catName = (String) cat.getSelectedItem();
            String matterName = matter.getSelectedItemPosition() == 0 ? "General Document Vault"
                    : (String) matter.getSelectedItem();
            LegalStore ls = new LegalStore(this);
            for (int i = 0; i < dlgUris.size(); i++) {
                Uri u = dlgUris.get(i);
                String label = dlgUris.size() == 1 ? base : base + " (" + (i + 1) + ")";
                String shown = label + extOf(displayName(u));
                pickedUris.add(u);
                pickedNames.add(shown);
                LegalStore.Doc d = new LegalStore.Doc();
                d.name = shown;
                d.caseName = matterName;
                d.category = catName;
                d.size = sizeText(fileSize(u));
                d.date = new SimpleDateFormat("dd MMM yyyy", Locale.US).format(new Date());
                ls.saveDoc(d);
            }
            dlgUris.clear();
            dlgRefresh = null;
            renderFiles();
            return true;
        }, null);
    }

    /** Ask: PDF from the phone, or a camera photo. replaceAt >= 0 swaps that file instead of adding. */
    private void chooseSource(int replaceAt) {
        replaceIdx = replaceAt;
        PickerDialogs.actions(this, replaceAt >= 0 ? "Replace file" : "Upload file",
                new String[]{"Upload PDF / Word / image", "Take photo"},
                new int[]{R.drawable.ic_pdf, R.drawable.ic_camera},
                i -> {
                    if (i == 0) pickFile();
                    else takePhoto();
                });
    }

    private void takePhoto() {
        try {
            File dir = getExternalFilesDir("case_photos");
            if (dir == null) dir = getCacheDir();
            dir.mkdirs();
            File f = new File(dir, "Photo_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date()) + ".jpg");
            cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_CAMERA);
        } catch (Exception e) {
            Toast.makeText(this, "Camera not available", Toast.LENGTH_LONG).show();
        }
    }

    private void openFile(int idx) {
        Uri u = pickedUris.get(idx);
        String type = getContentResolver().getType(u);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(u, type == null ? "*/*" : type);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "No app found to open this file", Toast.LENGTH_SHORT).show();
        }
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf", "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "image/*"});
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, replaceIdx < 0);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_FILE);
        } catch (Exception e) {
            Toast.makeText(this, "No file picker available on this device", Toast.LENGTH_LONG).show();
        }
    }

    private void addPicked(Uri uri) {
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // not every provider offers a persistable grant; the file still shows in the list
        }
        if (replaceIdx >= 0 && replaceIdx < pickedUris.size()) {
            pickedUris.set(replaceIdx, uri);
            pickedNames.set(replaceIdx, displayName(uri));
            replaceIdx = -1;
            return;
        }
        if (pickedUris.contains(uri)) return;
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

    private ImageView fileAction(int icon, int color, String desc, View.OnClickListener l) {
        ImageView x = CaseViews.icon(this, icon, color, 20);
        x.setPadding(dp(8), dp(8), dp(8), dp(8));
        x.setContentDescription(desc);
        x.setOnClickListener(l);
        return x;
    }

    private void swapFiles(int a, int b) {
        Collections.swap(pickedUris, a, b);
        Collections.swap(pickedNames, a, b);
    }

    private void renderFiles() {
        filesBox.removeAllViews();
        dragCur = -1;
        if (pickedUris.size() > 1) {
            TextView hint = CaseViews.text(this, "Press and hold a file, then drag up or down to reorder",
                    12, AppTheme.sub(this), false);
            hint.setPadding(0, 0, 0, dp(8));
            filesBox.addView(hint);
        }
        for (int i = 0; i < pickedUris.size(); i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(6), dp(2), dp(6));
            row.setBackground(CaseViews.box(this, AppTheme.field(this), 14, AppTheme.fieldStroke(this), 1));
            String lowName = pickedNames.get(i).toLowerCase(Locale.ROOT);
            boolean pdf = !(lowName.endsWith(".jpg") || lowName.endsWith(".jpeg") || lowName.endsWith(".png")
                    || lowName.endsWith(".webp"));

            ImageView grip = CaseViews.icon(this, R.drawable.ic_drag, AppTheme.sub(this), 18);
            grip.setContentDescription("Press and hold to move");
            row.addView(grip, new LinearLayout.LayoutParams(dp(26), dp(26)));
            ImageView kind = CaseViews.icon(this, pdf ? R.drawable.ic_pdf : R.drawable.ic_camera,
                    AppTheme.accent(this), 22);
            row.addView(kind);
            TextView n = CaseViews.text(this, pickedNames.get(i), 13, AppTheme.text(this), false);
            n.setSingleLine(true);
            n.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            n.setPadding(dp(10), 0, dp(4), 0);
            row.addView(n, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            row.addView(fileAction(R.drawable.ic_eye, AppTheme.accent(this), "View file",
                    v -> openFile(idx)), new LinearLayout.LayoutParams(dp(36), dp(36)));
            row.addView(fileAction(R.drawable.ic_replace, AppTheme.accent(this), "Replace file",
                    v -> PickerDialogs.confirm(this, "Replace file?",
                            "\"" + pickedNames.get(idx) + "\" will be replaced by a new file.",
                            "Replace", () -> chooseSource(idx))), new LinearLayout.LayoutParams(dp(36), dp(36)));
            row.addView(fileAction(R.drawable.ic_delete, RoundEdit.RED, "Remove file",
                    v -> PickerDialogs.confirm(this, "Delete file?",
                            "\"" + pickedNames.get(idx) + "\" will be removed from this case.",
                            "Delete", () -> {
                                pickedUris.remove(idx);
                                pickedNames.remove(idx);
                                renderFiles();
                            })), new LinearLayout.LayoutParams(dp(36), dp(36)));

            // long press -> drag up / down to reorder
            row.setOnLongClickListener(v -> {
                dragCur = filesBox.indexOfChild(v) - (pickedUris.size() > 1 ? 1 : 0);
                dragShift = 0;
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                v.setAlpha(0.9f);
                v.setElevation(dp(8));
                if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            });
            row.setOnTouchListener((v, e) -> {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) dragDownY = e.getRawY();
                if (dragCur < 0) return false;
                int off = pickedUris.size() > 1 ? 1 : 0;       // first child is the hint line
                if (filesBox.indexOfChild(v) - off != dragCur) return false;
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_MOVE: {
                        float ty = e.getRawY() - dragDownY + dragShift;
                        float step = v.getHeight() + dp(8);
                        int cur = dragCur;
                        if (ty > step * 0.55f && cur < pickedUris.size() - 1) {
                            View other = filesBox.getChildAt(cur + 1 + off);
                            swapFiles(cur, cur + 1);
                            filesBox.removeView(other);
                            filesBox.addView(other, cur + off);
                            dragCur = cur + 1;
                            dragShift -= step;
                            ty -= step;
                            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        } else if (ty < -step * 0.55f && cur > 0) {
                            View other = filesBox.getChildAt(cur - 1 + off);
                            swapFiles(cur, cur - 1);
                            filesBox.removeView(other);
                            filesBox.addView(other, cur + off);
                            dragCur = cur - 1;
                            dragShift += step;
                            ty += step;
                            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        }
                        v.setTranslationY(ty);
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(false);
                        renderFiles();      // drop: rebuild rows in the new order
                        return true;
                    default:
                        return true;
                }
            });

            LinearLayout.LayoutParams lp = lpTop(0);
            lp.bottomMargin = dp(8);
            filesBox.addView(row, lp);
        }
        filesBox.setPadding(dp(16), 0, dp(16), pickedUris.isEmpty() ? 0 : dp(8));
        if (scaled) shrink(filesBox);
    }

    // ---------- parties: field + chip look ----------

    private TextView partyLabel(String s) {
        TextView t = CaseViews.text(this, s, 14, AppTheme.text(this), false);
        t.setPadding(0, dp(16), 0, 0);
        return t;
    }

    private static final String[] CLIENT_SIDES = {"Petitioner", "Respondent", "Complainant", "Accused",
            "Appellant", "Plaintiff", "Defendant", "Applicant"};
    private static final int[] CLIENT_ICONS = {R.drawable.ic_person, R.drawable.ic_contacts,
            R.drawable.ic_info, R.drawable.ic_shield, R.drawable.ic_notes,
            R.drawable.ic_person_add, R.drawable.ic_briefcase, R.drawable.ic_edit};

    private LinearLayout clientSideCard;

    /** Red border on the whole "My client is" card while nothing is chosen after a failed Create. */
    private void clientSideInvalid(boolean bad) {
        if (clientSideCard == null) return;
        clientSideCard.setBackground(CaseViews.box(this, AppTheme.card(this), 16,
                bad ? RoundEdit.RED : AppTheme.cardStroke(this), bad ? 2 : 1));
    }

    /**
     * "My client is" (mandatory): bordered card like the Client card, all options as single-select
     * chips inside it, three per row, each with its own border. Icon on top, full name below,
     * chosen one gets a thick outline and a check. Tap the chosen one again to clear.
     */
    private View clientSideRow() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        clientSideCard = card;
        clientSideInvalid(false);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(CaseViews.text(this, "My client is *", 15, AppTheme.text(this), false),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(CaseViews.text(this, "Required", 12, AppTheme.sub(this), false));
        card.addView(head);

        final LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        card.addView(grid, lpTop(12));
        final Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            grid.removeAllViews();
            final int perRow = 3;
            for (int start = 0; start < CLIENT_SIDES.length; start += perRow) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                for (int k = 0; k < perRow; k++) {
                    int i = start + k;
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                    if (k < perRow - 1) lp.rightMargin = dp(8);
                    if (i >= CLIENT_SIDES.length) {          // keep the last row's chips the same width
                        row.addView(new View(this), lp);
                        continue;
                    }
                    final String side = CLIENT_SIDES[i];
                    boolean on = side.equals(clientRole);
                    LinearLayout chip = new LinearLayout(this);
                    chip.setOrientation(LinearLayout.VERTICAL);
                    chip.setGravity(Gravity.CENTER);
                    chip.setPadding(dp(4), dp(10), dp(4), dp(10));
                    chip.setBackground(CaseViews.box(this, AppTheme.field(this), 14,
                            on ? AppTheme.text(this) : AppTheme.fieldStroke(this), on ? 2 : 1));
                    int fg = on ? AppTheme.text(this) : AppTheme.sub(this);
                    chip.addView(CaseViews.icon(this, on ? R.drawable.ic_check_circle : CLIENT_ICONS[i], fg, 20));
                    TextView t = CaseViews.text(this, side, 12, fg, on);
                    t.setGravity(Gravity.CENTER);
                    t.setSingleLine(false);
                    t.setMaxLines(2);
                    t.setPadding(0, dp(4), 0, 0);
                    chip.addView(t, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    chip.setOnClickListener(v -> {
                        clientRole = side.equals(clientRole) ? "" : side;
                        if (!clientRole.isEmpty()) clientSideInvalid(false);
                        draw[0].run();
                        refresh();
                    });
                    row.addView(chip, lp);
                }
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                if (start > 0) rl.topMargin = dp(8);
                grid.addView(row, rl);
            }
            if (scaled) shrink(grid);
        };
        draw[0].run();
        return card;
    }

    // ---------- several names: petitioners, respondents, opposing advocates ----------

    private void multiNames(LinearLayout f, String label, String addLabel, String hint, List<EditText> rows) {
        f.addView(partyLabel(label));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        f.addView(box, lpTop(8));
        android.widget.Button add = LegalUi.outlineButton(this, "+ " + addLabel);
        add.setOnClickListener(v -> addNameRow(box, rows, hint));
        f.addView(add, lpTop(10));
        addNameRow(box, rows, hint);
    }

    private void addNameRow(LinearLayout box, List<EditText> rows, String hint) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final EditText e = new RoundEdit(this);
        e.setInputType(NAME);
        e.setHint(hint);
        e.setSingleLine(true);
        ImageView x = CaseViews.icon(this, R.drawable.ic_close, AppTheme.sub(this), 20);
        x.setPadding(dp(10), dp(10), dp(10), dp(10));
        x.setContentDescription("Remove");
        rows.add(e);
        x.setOnClickListener(v -> {
            if (rows.size() == 1) {
                e.setText("");
                return;
            }
            box.removeView(row);
            rows.remove(e);
        });
        row.addView(e, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(x, new LinearLayout.LayoutParams(dp(40), dp(40)));
        box.addView(row, lpTop(box.getChildCount() == 0 ? 0 : 8));
        if (scaled) shrink(row);
    }

    /** All filled names joined with ", ". */
    private String names(List<EditText> rows) {
        StringBuilder sb = new StringBuilder();
        for (EditText e : rows) {
            String v = LegalUi.str(e);
            if (v.isEmpty()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(v);
        }
        return sb.toString();
    }

    // ---------- current / next stage (editable list) ----------

    private void openStages(boolean current) {
        PickerDialogs.stageSheet(this, current ? "Current Stage" : "Next Status",
                () -> stages.list(), current ? curStatus : nextStatus,
                s -> {
                    if (current) {
                        curStatus = s;
                        setChosen(curStatusText, s, "Select Current Stage");
                    } else {
                        nextStatus = s;
                        setChosen(nextStatusText, s, "Select Status");
                    }
                },
                s -> stages.add(s),
                (old, neu) -> {
                    stages.rename(old, neu);
                    for (DiaryStore.HCase cs : store.cases()) {
                        boolean ch = false;
                        if (old.equals(cs.proceeding)) { cs.proceeding = neu; ch = true; }
                        if (old.equals(cs.nextStatus)) { cs.nextStatus = neu; ch = true; }
                        if (ch) store.saveCase(cs);
                    }
                    if (old.equals(curStatus)) {
                        curStatus = neu;
                        setChosen(curStatusText, neu, "Select Current Stage");
                    }
                    if (old.equals(nextStatus)) {
                        nextStatus = neu;
                        setChosen(nextStatusText, neu, "Select Status");
                    }
                },
                gone -> {
                    stages.delete(gone);
                    if (gone.equals(curStatus)) {
                        curStatus = "";
                        setChosen(curStatusText, "", "Select Current Stage");
                    }
                    if (gone.equals(nextStatus)) {
                        nextStatus = "";
                        setChosen(nextStatusText, "", "Select Status");
                    }
                },
                (i, j) -> stages.swap(i, j));
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
        EditText a = new RoundEdit(this);
        a.setHint("Act (e.g. IPC)");
        a.setSingleLine(true);
        a.setTextSize(14);
        EditText s = new RoundEdit(this);
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

    // ---------- judge picker, status list, client role ----------

    private List<String> judgeNames() {
        List<String> out = new ArrayList<>();
        List<DiaryStore.Judge> all = store.judges();
        java.util.Collections.reverse(all);
        for (DiaryStore.Judge j : all) {
            if (j.name.isEmpty()) continue;
            boolean dup = false;
            for (String s : out) if (s.equalsIgnoreCase(j.name)) { dup = true; break; }
            if (!dup) out.add(j.name);
        }
        return out;
    }

    private void pickJudge(String name) {
        judgeName = name;
        setChosen(judgeText, name, "Select Judge");
        for (DiaryStore.Judge j : store.judges()) {
            if (j.name.equalsIgnoreCase(name) && !j.room.isEmpty() && LegalUi.str(roomEdit).isEmpty()) {
                roomEdit.setText(j.room);   // saved room for this judge
                break;
            }
        }
    }

    private void addJudgeDialog() {
        PickerDialogs.customName(this, "Add New Judge", "Judge Name*", "Enter judge name",
                "Judge name is required", s -> {
                    String name = s;
                    for (String t : judgeNames()) if (t.equalsIgnoreCase(s)) { name = t; break; }
                    saveJudge(name, "");
                    pickJudge(name);
                });
    }

    private void deleteJudge(String name) {
        List<DiaryStore.Judge> l = store.judges();
        l.removeIf(j -> j.name.equalsIgnoreCase(name));
        store.saveJudges(l);
        if (name.equalsIgnoreCase(judgeName)) {
            judgeName = "";
            setChosen(judgeText, "", "Select Judge");
        }
    }

    /** Add the judge if new; remember the room / court if given. */
    private void saveJudge(String name, String room) {
        if (name.isEmpty()) return;
        List<DiaryStore.Judge> l = store.judges();
        DiaryStore.Judge hit = null;
        for (DiaryStore.Judge j : l) if (j.name.equalsIgnoreCase(name)) { hit = j; break; }
        if (hit == null) {
            hit = new DiaryStore.Judge();
            hit.name = name;
            l.add(hit);
        }
        if (!room.isEmpty()) hit.room = room;
        if (!courtName.isEmpty()) hit.court = courtName;
        store.saveJudges(l);
    }


    private void addFirSectionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        EditText s = new RoundEdit(this);
        s.setHint("Section (e.g. 420 IPC)");
        s.setSingleLine(true);
        s.setTextSize(14);
        ImageView x = CaseViews.icon(this, R.drawable.ic_close, AppTheme.sub(this), 20);
        x.setPadding(dp(10), dp(10), dp(10), dp(10));
        x.setContentDescription("Remove section");
        firSecRows.add(s);
        x.setOnClickListener(v -> {
            if (firSecRows.size() == 1) { s.setText(""); return; }
            firSecBox.removeView(row);
            firSecRows.remove(s);
        });
        row.addView(s, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(x, new LinearLayout.LayoutParams(dp(40), dp(40)));
        if (scaled) shrink(row);
        firSecBox.addView(row);
    }

    /** FIR sections joined with ", " (blank rows skipped). */
    private String firSectionsText() {
        StringBuilder sb = new StringBuilder();
        for (EditText e : firSecRows) {
            String v = LegalUi.str(e);
            if (v.isEmpty()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(v);
        }
        return sb.toString();
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
        boolean noTitle = LegalUi.str(titleEdit).isEmpty();
        boolean noReg = LegalUi.str(regEdit).isEmpty();
        boolean noNo = LegalUi.str(caseNoEdit).isEmpty();
        ((RoundEdit) titleEdit).setInvalid(noTitle);
        ((RoundEdit) regEdit).setInvalid(noReg);
        ((RoundEdit) caseNoEdit).setInvalid(noNo);
        dropInvalid(courtNameText, courtName.isEmpty());
        dropInvalid(caseTypeText, caseType.isEmpty());
        clientSideInvalid(clientRole.isEmpty());
        if (clientRole.isEmpty()) m.add("My client is");
        if (noTitle) m.add("Case Title");
        if (noReg) m.add("Registration Date");
        if (courtName.isEmpty()) m.add("Court name / type");
        if (caseType.isEmpty()) m.add("Case type");
        if (noNo) m.add("Case No.");
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
        line(sb, "Petitioner / Plaintiff / First Party", names(petRows));
        line(sb, "Respondent / Defendant / Opposite Party", names(resRows));
        line(sb, "Opposing advocate", names(advRows));
        line(sb, "Agreed fee", LegalUi.str(feeEdit));
        line(sb, "My client is", clientRole);
        line(sb, "Judge", judgeName);
        line(sb, "Court room / address", LegalUi.str(roomEdit));
        line(sb, "Next hearing", LegalUi.str(nextHearingEdit));
        line(sb, "Current status", curStatus);
        line(sb, "Next status", nextStatus);
        String fn = LegalUi.str(firNoEdit), fy = LegalUi.str(firYearEdit);
        line(sb, "FIR", (LegalUi.str(stationEdit) + " " + fn + (fy.isEmpty() ? "" : "/" + fy) + " "
                + LegalUi.str(firDateEdit)).trim());
        line(sb, "FIR under section(s)", firSectionsText());
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

        String pet = names(petRows);
        c.first = pet.isEmpty() ? c.caseTitle : pet;
        c.second = names(resRows);
        c.appearingFor = clientRole;
        c.opposingAdvocate = names(advRows);
        c.representing = cl != null ? cl.name : clientRole;

        c.cnr = cnrValue();
        c.filingNo = LegalUi.str(filingEdit);
        c.diaryNo = LegalUi.str(diaryEdit);
        c.billTotal = LegalUi.num(feeEdit);
        c.feeNotes = LegalUi.str(feeNotesEdit);
        c.judge = judgeName;
        c.room = LegalUi.str(roomEdit);
        c.next = LegalUi.str(nextHearingEdit);
        c.proceeding = curStatus;
        c.nextStatus = nextStatus;
        c.clientRole = clientRole;
        saveJudge(judgeName, c.room);
        c.priority = String.valueOf(priority.getSelectedItem());
        c.subType = LegalUi.str(subTypeEdit);
        c.notes = LegalUi.str(notesEdit);
        c.firStation = LegalUi.str(stationEdit);
        c.firNo = LegalUi.str(firNoEdit);
        c.firYear = LegalUi.str(firYearEdit);
        c.firSections = firSectionsText();
        c.firDate = LegalUi.str(firDateEdit);
        c.acts = actsText();

        store.saveCase(c);
        Toast.makeText(this, "Case created", Toast.LENGTH_SHORT).show();
        finish();
    }
}
