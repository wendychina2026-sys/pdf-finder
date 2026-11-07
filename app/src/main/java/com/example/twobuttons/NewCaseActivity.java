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
    private static final int REQ_SAVE = 54;
    private static final int REQ_SCAN = 55;
    private static final String[] SRC_LABELS = {"Upload PDF / Word / image", "Take photo"};
    private static final int[] SRC_ICONS = {R.drawable.ic_pdf, R.drawable.ic_camera};
    private static final int REQUIRED = 6;
    /** True once a document was saved to the General Document Vault: case fields are then optional. */
    private boolean vaultSaved = false;
    private TextView createBtn;
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
    private String referredBy = "";
    private TextView referredTv;
    private boolean autoMode = false;   // opens on "Enter manually"

    private TextView kindText, courtNameText, caseTypeText, cnrTitle, progressText, remainText;
    private LinearLayout cnrCard, clientBox, actsBox, segAuto, segManual;
    private View progFill, progRest;
    private EditText cnrLookup, titleEdit, caseNoEdit, regEdit;
    private EditText filingYearEdit, cnrId, filingEdit, feeEdit, feeNotesEdit;
    private final List<EditText> petRows = new ArrayList<>();   // several petitioners
    private final List<EditText> resRows = new ArrayList<>();   // several respondents
    private final List<EditText> advRows = new ArrayList<>();   // several opposing advocates
    private StageStore stages;
    private String judgeName = "", curStatus = "", nextStatus = "", clientRole = "";
    private TextView judgeText, curStatusText, nextStatusText;
    private EditText roomEdit, nextHearingEdit;
    private String subTypeVal = "";
    private EditText caseYearEdit;
    private LinearLayout extraBox, idForm, idSection;
    private View extraHead;
    private TextView subTypeText;
    private EditText notesEdit, stationEdit, firNoEdit, firDateEdit;
    private Spinner priority;
    private final List<EditText[]> actRows = new ArrayList<>();
    private final List<EditText> firSecRows = new ArrayList<>();
    private LinearLayout firSecBox;
    private EditText firYearEdit;
    private final List<Uri> pickedUris = new ArrayList<>();
    private final List<String> pickedNames = new ArrayList<>();
    private final List<List<Uri>> pickedMore = new ArrayList<>();   // further files of the same group
    private final List<String[]> pickedMeta = new ArrayList<>();   // per file: category, matter, stage, size, date
    private int replaceIdx = -1;          // >= 0 while a file is being replaced
    private Uri cameraUri;
    private final PhotoFlow flow = new PhotoFlow(this, REQ_SCAN);
    // upload dialog state: files chosen inside the open dialog, and a callback that redraws its drop area
    private final List<Uri> dlgUris = new ArrayList<>();
    private Runnable dlgRefresh = null;
    private int dlgReplaceIdx = -1;       // >= 0 while a file in the upload dialog is being replaced
    private int dragCur = -1;             // row being dragged, -1 = none
    private float dragDownY, dragShift;
    private LinearLayout filesBox;
    private boolean scaled = false;

    private final TextWatcher watcher = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void afterTextChanged(Editable s) { refresh(); }
    };

    /** Intent extra: path of a PDF from the Category marker; opens the upload popup with it loaded. */
    static final String EXTRA_UPLOAD_PDF = "upload_pdf";
    /** Intent extra: the Attach to Matter choice made in the marker (vault, saved case or Create new case). */
    static final String EXTRA_UPLOAD_MATTER = "upload_matter";
    /** Intent extra: paths of the OTHER shared files (images / Word) that join the first one in the upload popup. */
    static final String EXTRA_UPLOAD_EXTRA = "upload_extra";
    private String markerPdf = "";
    private java.util.ArrayList<String> markerExtras = new java.util.ArrayList<>();
    private String markerMatter = "";
    private int markerMulti = MultiDocDetector.ASK;     // shared files: did the user confirm "details per file"?
    private static final String DLG_GENERAL = "-- General Document Vault --";
    private android.app.Dialog dlgSheet;          // the open Upload Legal Document sheet (multi mode greys its Save button)
    /** Details per file in "multi" mode, keyed by file uri: name, category, stage, matter, file numbers (pages). */
    private final java.util.Map<String, String[]> dlgMeta = new java.util.HashMap<>();
    /** Note per file in the upload box, keyed by file uri (orange + blinking icon when filled). */
    private final java.util.Map<String, String> dlgNotes = new java.util.HashMap<>();
    /** Files that came from the Category marker (content uri -> real path): View opens them in the marker. */
    private final java.util.Map<String, String> markerFiles = new java.util.HashMap<>();
    /** Uris of documents saved with "Create new case": they join this case when it is created. */
    private final List<String> pendingNewCaseDocs = new ArrayList<>();
    /** Lead file of the latest card saved to the General Document Vault from this page: Additional details > Notes is that card's note. */
    private String vaultCardPath = "";

    /** Copies the Notes box of this page into the note of the Vault card saved here (the card's own Notes icon shows it). */
    private void mirrorNoteToVaultCard() {
        if (vaultCardPath.isEmpty() || notesEdit == null) return;
        String v = LegalUi.str(notesEdit);
        android.content.SharedPreferences.Editor e = getSharedPreferences("doc_notes", MODE_PRIVATE).edit();
        if (v.isEmpty()) e.remove("card:" + vaultCardPath);
        else e.putString("card:" + vaultCardPath, v);
        e.apply();
    }
    private LinearLayout uploadCardView;
    private static final String MULTIPLE = "Multiple";
    private static final String CREATE_NEW_CASE = "Create new case";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().hide();
        String mp = getIntent().getStringExtra(EXTRA_UPLOAD_PDF);
        markerPdf = (mp == null || savedInstanceState != null) ? "" : mp;
        java.util.ArrayList<String> mx = getIntent().getStringArrayListExtra(EXTRA_UPLOAD_EXTRA);
        markerExtras = (mx == null || savedInstanceState != null) ? new java.util.ArrayList<>() : mx;
        markerMulti = savedInstanceState != null ? MultiDocDetector.ASK
                : getIntent().getIntExtra(MultiDocDetector.EXTRA_MODE, MultiDocDetector.ASK);
        String mm = getIntent().getStringExtra(EXTRA_UPLOAD_MATTER);
        markerMatter = (mm == null || savedInstanceState != null) ? "" : mm;
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
        if (!markerPdf.isEmpty()) root.post(this::uploadDialog);
    }

    // ---------- small helpers ----------

    /** Scales every text on this page down a little; views added later call this for themselves. */
    private void shrink(View v) {
        if (v instanceof TextView && !(v.getTag() instanceof View) && !"keep".equals(v.getTag())) {   // dropdown text / labels stay as set
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
        return CaseViews.fieldLabel(this, s);
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
        return FieldStyle.dropdown(this, parent, placeholder, click, lpTop(0));
    }

    private void dropInvalid(TextView t, boolean invalid) {
        FieldStyle.setDropdownInvalid(t, invalid);
    }

    private void setChosen(TextView t, String value, String placeholder) {
        boolean has = value != null && !value.isEmpty();
        if (has) dropInvalid(t, false);
        FieldStyle.setDropdownValue(t, value, placeholder);
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
        if (vaultSaved) {                       // reference document saved: nothing is mandatory any more
            boolean none = done == 0;
            progressText.setText("Document saved to Vault");
            remainText.setText(none ? "Case details are optional - tap Done to finish"
                    : "Case details are optional");
            if (createBtn != null) createBtn.setText(none ? "Done" : "+  Create Case");
            return;
        }
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
        FieldStyle.hint(cnrLookup, "Enter CNR Number");
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
        LegalUi.label(this, c, "Referred by");
        referredTv = FieldStyle.dropdown(this, c, "Select or add reference person", v -> pickReferrer(),
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        FieldStyle.setDropdownValue(referredTv, referredBy, "Select or add reference person");
        return c;
    }

    /** Referred by: saved client names, or a reference person added from the phone's contacts. */
    private void pickReferrer() {
        final List<String> names = new ArrayList<>();
        for (LegalStore.Client cl : legal.clients()) if (!cl.name.isEmpty() && !names.contains(cl.name)) names.add(cl.name);
        final List<String> items = new ArrayList<>();
        items.add("+ Add reference person from contacts");
        if (!referredBy.isEmpty()) items.add("Clear");
        items.addAll(names);
        PickerDialogs.optionSheet(this, "Referred by", "Saved clients", items, items.indexOf(referredBy), i -> {
            String s = items.get(i);
            if (i == 0) {
                if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    startActivityForResult(new Intent(this, ChooseContactActivity.class), REQ_REF_CONTACT);
                } else {
                    requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS}, REQ_REF_PERM);
                }
                return;
            }
            referredBy = s.equals("Clear") ? "" : s;
            FieldStyle.setDropdownValue(referredTv, referredBy, "Select or add reference person");
        });
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
        FieldStyle.hint(search, "Search clients...");
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
        if (all.isEmpty()) {
            search.setVisibility(View.GONE);
            lv.setVisibility(View.GONE);
            TextView none = CaseViews.text(this, "No earlier client saved", 15, AppTheme.sub(this), false);
            none.setGravity(Gravity.CENTER);
            none.setPadding(0, dp(24), 0, dp(8));
            box.addView(none, 0);
        }

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
        if (req == REQ_SAVE) {
            DocFileCard.finishSave(this, res, data);
            return;
        }
        if (FileSetDialog.handle(this, req, res, data)) return;
        if (flow.handle(req, res)) return;
        if (req == REQ_REF_CONTACT) {
            if (res == RESULT_OK && data != null && data.getStringExtra("name") != null) {
                referredBy = data.getStringExtra("name");
                FieldStyle.setDropdownValue(referredTv, referredBy, "Select or add reference person");
            }
            return;
        }
        if (req == REQ_ADV_CONTACT && res == RESULT_OK && data != null && advTarget != null) {
            setAdvocate(advTarget, data.getStringExtra("name"), data.getStringExtra("number"), advTarget.card);
            return;
        }
        if (req == REQ_ADV_IMG) {
            if (res == RESULT_OK && data != null && data.getData() != null && advTarget != null) {
                try {
                    storeCard(advTarget, getContentResolver().openInputStream(data.getData()));
                } catch (Exception e) {
                    Toast.makeText(this, "Could not read that image", Toast.LENGTH_SHORT).show();
                }
            }
            return;
        }
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
            dlgReplaceIdx = -1;
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
        f.addView(lbl("Court"));
        kindText = dropdown(f, CHOOSE_COURT, v -> openCourtKinds());
        setChosen(kindText, courtKind, CHOOSE_COURT);

        f.addView(lbl("Court name / type *"));
        courtNameText = dropdown(f, "Select Court Type", v ->
                PickerDialogs.courtTypes(this, this::courtTypes, courtName, this::addCourtDialog,
                        this::isCustomCourt, this::deleteCourtType, s -> {
                            courtName = s;
                            setChosen(courtNameText, s, "Select Court Type");
                            refresh();
                        }));
        f.addView(lbl("Current Stage"));
        curStatusText = dropdown(f, "Select Current Stage", v -> openStages(true));
        titleEdit = LegalUi.input(this, f, "Case Title*", "", TEXT);
        ((RoundEdit) titleEdit).setRequired(true);
        FieldStyle.hint(titleEdit, "Enter Case Title");
        regEdit = LegalUi.dmyInput(this, f, "Registration Date*", "");
        FieldStyle.hint(regEdit, "Select Date");
        regEdit.setOnClickListener(v ->
                PickerDialogs.calendar(this, "Select Registration Date", regEdit));

        f.addView(lbl("Case type*"));
        caseTypeText = dropdown(f, "Select Particular", v ->
                choose("Case type", Arrays.asList(DiaryStore.CASE_TYPES), s -> {
                    caseType = s;
                    setChosen(caseTypeText, s, "Select Particular");
                    refresh();
                }));
        nextHearingEdit = LegalUi.dmyInput(this, f, "Next hearing date", "");
        FieldStyle.hint(nextHearingEdit, "Select Date");
        nextHearingEdit.setOnClickListener(v ->
                PickerDialogs.calendar(this, "Select Next Hearing Date", nextHearingEdit));
        f.addView(lbl("Next status"));
        nextStatusText = dropdown(f, "Select Status", v -> openStages(false));
        c.addView(f, lpTop(4));

        titleEdit.addTextChangedListener(watcher);
        regEdit.addTextChangedListener(watcher);
        return c;
    }

    /** First court list. A sub-level's cross comes back here. */
    private void openCourtKinds() {
        PickerDialogs.courtSheet(this, COURT_KINDS, COURT_ICONS, CourtPicker.base(courtKind),
                s -> CourtPicker.pick(this, s, this::openCourtKinds, path -> {
                    courtKind = path;
                    setChosen(kindText, path, CHOOSE_COURT);
                    cnrTitle.setText(cnrHeading());
                }));
    }

    private String cnrHeading() {
        return courtKind.isEmpty() ? "Case Search & Auto-Fill" : CourtPicker.base(courtKind) + " CNR Lookup";
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
        extraBox = extra;
        extraHead = head;
        head.setOnClickListener(v -> {
            boolean open = extra.getVisibility() != View.VISIBLE;
            extra.setVisibility(open ? View.VISIBLE : View.GONE);
            toggle.setImageResource(open ? R.drawable.ic_remove_circle : R.drawable.ic_add_circle);
        });

        // case identifiers
        LinearLayout f1 = subForm();
        idForm = f1;
        EditText[] fl = numberYear(f1, "Filing\\Diary number", "Year");
        filingEdit = fl[0];
        filingYearEdit = fl[1];
        cnrId = LegalUi.input(this, f1, "CNR number", "", CAPS);
        f1.addView(lbl("Sub-case type"));
        subTypeText = dropdown(f1, "Select Sub-case type", v ->
                PickerDialogs.searchSheet(this, "Sub-case type", null, Arrays.asList(SubCaseTypes.ALL),
                        subTypeVal, null, null, null, s -> {
                            subTypeVal = s;
                            setChosen(subTypeText, s, "Select Sub-case type");
                        }, null));
        EditText[] cn = numberYear(f1, "Case No.*", "Case year");
        caseNoEdit = cn[0];
        caseYearEdit = cn[1];
        ((RoundEdit) caseNoEdit).setRequired(true);
        FieldStyle.hint(caseNoEdit, "Case No.");
        caseNoEdit.addTextChangedListener(watcher);
        idSection = section(R.drawable.ic_tag, "Case identifiers",
                "Filing\\Diary no. and year, CNR, sub-case type, case no. and year", f1);
        extra.addView(idSection);

        // parties
        LinearLayout f2 = subForm();
        multiNames(f2, "Petitioner / Plaintiff / First Party", "Add",
                "Enter Petitioner / First Party", petRows, 0xFF1565C0, 0xFF64B5F6);
        multiNames(f2, "Respondent / Defendant / Opposite Party", "Add",
                "Enter Respondent / Opposite Party", resRows, 0xFFE65100, 0xFFFFB74D);
        extra.addView(section(R.drawable.ic_contacts, "Parties",
                "Petitioners and respondents", f2));

        extra.addView(section(R.drawable.ic_briefcase, "Opposing advocate",
                "Name, contact and visiting card", buildAdvocateForm()));

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
        FieldStyle.hint(roomEdit, "Court room no. and address");
        extra.addView(section(R.drawable.ic_court, "Judge & hearing",
                "Judge and court room / address", fj));

        LinearLayout f4 = subForm();
        priority = LegalUi.spinner(this, f4, "Priority", PRIORITIES, PRIORITIES[0]);
        notesEdit = LegalUi.multiline(this, f4, "Notes", "");
        notesEdit.setMinLines(3);
        notesEdit.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence cs, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence cs, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable ed) { mirrorNoteToVaultCard(); }
        });
        extra.addView(section(R.drawable.ic_edit, "Notes & other details",
                "Priority, notes and sub type", f4));

        // FIR
        LinearLayout f5 = subForm();
        stationEdit = LegalUi.text(this, f5, "Police station", "");
        EditText[] fir = numberYear(f5, "FIR number", "FIR year");
        firNoEdit = fir[0];
        firYearEdit = fir[1];
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
        FieldStyle.hint(firDateEdit, "Select Date");
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
        uploadCardView = c;
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
        if (dlgReplaceIdx >= 0 && dlgReplaceIdx < dlgUris.size()) {
            dlgSet(dlgReplaceIdx, uri);
            dlgReplaceIdx = -1;
            return;
        }
        dlgUris.add(uri);
        dlgPagesReset();
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

    /** Document name + category + matter (+ stage), then "Tap to choose" (PDF / Word / image, or a photo). */
    private void uploadDialog() {
        dlgUris.clear();
        dlgMeta.clear();
        dlgNotes.clear();
        final int multiIn = markerMulti;
        markerMulti = MultiDocDetector.ASK;
        // the PDF handed over by the Category marker is used once, for the popup opened on arrival
        final String markerPath = markerPdf;
        markerPdf = "";
        final String markerMatterPick = markerMatter;
        markerMatter = "";
        final java.util.ArrayList<String> extraPaths = markerExtras;
        markerExtras = new java.util.ArrayList<>();
        final java.io.File marked = markerPath.isEmpty() ? null : new java.io.File(markerPath);
        final boolean fromMarker = marked != null && marked.isFile();
        // only a PDF coming from the Category marker had a matter chosen there; a shared image / Word file did not
        final boolean markerChoseMatter = fromMarker && marked.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
        final java.util.Map<Integer, String> marks = fromMarker
                ? new MarkStore(this).all(markerPath) : new java.util.HashMap<>();
        if (fromMarker) {
            Uri mu = PhotoFlow.uri(this, marked);
            dlgUris.add(mu);
            markerFiles.put(mu.toString(), markerPath);
            for (String xp : extraPaths) {          // every other file shared together with the first
                java.io.File xf = new java.io.File(xp);
                if (!xf.isFile()) continue;
                Uri xu = PhotoFlow.uri(this, xf);
                if (dlgUris.contains(xu)) continue;
                dlgUris.add(xu);
                markerFiles.put(xu.toString(), xp);
            }
        }
        replaceIdx = -1;
        dlgReplaceIdx = -1;
        final String general = "-- General Document Vault --";
        final String[] catSel = {fromMarker ? markedCategory(marks) : "Other"};
        final boolean[] autoCat = {fromMarker && !marks.isEmpty()};   // category still follows the marked pages
        final List<DiaryStore.HCase> cases = store.cases();
        final String[] matterSel = {markerChoseMatter ? CREATE_NEW_CASE : general};
        if (fromMarker && !markerMatterPick.isEmpty()) {      // the choice made in the marker's Attach to Matter
            if (markerMatterPick.equals(MatterPicker.VAULT) || markerMatterPick.equals("General Document Vault")) {
                matterSel[0] = general;
            } else if (!markerMatterPick.equals("+ Create new case")) {
                for (DiaryStore.HCase hc : cases) {
                    String t = (!hc.first.isEmpty() && !hc.second.isEmpty()) ? hc.first + " vs. " + hc.second : hc.title();
                    if (t.equals(markerMatterPick) || hc.title().equals(markerMatterPick)) matterSel[0] = hc.title();
                }
            }
        }
        final String[] stageSel = {""};
        final boolean[] dupOk = {false};
        final int[] wholeMode = {-1};              // multi-page PDF, one category: -1 not asked, 0 all pages, 1 some pages, 2 mark later
        final String[] wholeRange = {""};
        final boolean[] multi = {multiIn == MultiDocDetector.YES};     // details are set per file (Edit details)
        dlgMultiNow = () -> multi[0];
        dlgMatterNow = () -> matterSel[0];
        final boolean[] asked = {multiIn != MultiDocDetector.ASK};      // "more than one file" already answered
        final boolean[] matterTouched = {false};   // matter changed in this popup (wins over the marker's latest pick)

        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);

        // all labels / boxes use the same helpers and scale as the New Case page
        final TextView nameLbl = dlgLbl("Document Name / Caption *");
        f.addView(nameLbl);
        EditText name = new RoundEdit(this);
        FieldStyle.smallHint(name, "e.g. Interim Injunction Application");
        name.setSingleLine(true);
        f.addView(name, lpTop(0));

        final TextView catLbl = dlgLbl("Document Category");
        f.addView(catLbl);
        TextView catTv = dropdown(f, "Select category", null);
        setChosen(catTv, catSel[0], "Select category");
        // marks changed (page re-marked, category renamed / deleted): "Multiple" follows them
        final Runnable syncCat = () -> {
            if (!autoCat[0]) return;
            catSel[0] = markedCategory(marks);
            if (marks.isEmpty()) autoCat[0] = false;
            setChosen(catTv, catSel[0], "Select category");
        };
        ((View) catTv.getTag()).setOnClickListener(v -> {
            if (fromMarker) {
                // same view as the Category marker: marked categories checked in green, plus edit pages / rename / delete
                CategoryPicker.pick(this, autoCat[0] ? "" : catSel[0], c -> {
                    catSel[0] = c;
                    autoCat[0] = false;
                    setChosen(catTv, catSel[0], "Select category");
                }, marks, CategoryPicker.manage(this, markerPath, marks, syncCat), autoCat[0]);
                return;
            }
            CategoryPicker.pick(this, catSel[0], c -> {
                catSel[0] = c;
                setChosen(catTv, catSel[0], "Select category");
            });
        });

        // Stage sits under Document Category and is always available
        final TextView stageLbl = dlgLbl("Stage");
        final LinearLayout stageBox = new LinearLayout(this);
        stageBox.setOrientation(LinearLayout.VERTICAL);
        stageBox.addView(stageLbl);
        TextView stageTv = dropdown(stageBox, "Select stage", null);
        // hearing stages: same list as Filter cases > Hearing stage and New Case > Current Stage
        final StageStore docStages = new StageStore(this);
        ((View) stageTv.getTag()).setOnClickListener(v ->
                PickerDialogs.stageSheet(this, "Stage", () -> docStages.list(), stageSel[0],
                        s -> {
                            stageSel[0] = s;
                            setChosen(stageTv, s, "Select stage");
                        },
                        s -> docStages.add(s),
                        (old, neu) -> {
                            docStages.rename(old, neu);
                            if (old.equals(stageSel[0])) {
                                stageSel[0] = neu;
                                setChosen(stageTv, neu, "Select stage");
                            }
                        },
                        gone -> {
                            docStages.delete(gone);
                            if (gone.equals(stageSel[0])) {
                                stageSel[0] = "";
                                setChosen(stageTv, "", "Select stage");
                            }
                        },
                        (i, j) -> docStages.swap(i, j)));
        f.addView(stageBox);

        f.addView(dlgLbl("Attach to Matter (Optional)"));
        TextView matterTv = dropdown(f, general, null);
        setChosen(matterTv, matterSel[0], general);


        ((View) matterTv.getTag()).setOnClickListener(v -> {
            List<String> items = new ArrayList<>();
            items.add(general);
            if (fromMarker && !multi[0]) items.add(CREATE_NEW_CASE);
            for (DiaryStore.HCase hc : cases) items.add(hc.title());
            if (multi[0]) items.add(CREATE_NEW_CASE);          // last
            PickerDialogs.optionSheet(this, "Attach to Matter", "Saved cases", items,
                    items.indexOf(matterSel[0]), i -> {
                        final String pick = items.get(i);
                        if (pick.equals(matterSel[0])) return;
                        final Runnable apply = () -> {
                            matterTouched[0] = true;
                            matterSel[0] = pick;
                            setChosen(matterTv, matterSel[0], general);
                        };
                        if (markerChoseMatter && !multi[0]) {       // it was chosen in the marker already: ask before changing it
                            PickerDialogs.confirm(this, "Change matter?", "Attach to Matter is \"" + matterSel[0]
                                    + "\". Change it to \"" + pick + "\"?", "Change", apply);
                        } else {
                            apply.run();
                        }
                    });
        });
        // multi mode: name / category / stage / matter are set per file, so these boxes are switched off
        final Runnable applyMulti = () -> {
            // name / category / stage are set per file (Edit details): not shown here at all
            name.setText("");
            for (View hv : new View[]{nameLbl, name, catLbl, (View) catTv.getTag(), stageBox}) hv.setVisibility(View.GONE);
            autoCat[0] = false;
            // Attach to Matter stays usable; files start on "Create new case" (the case being made on this page)
            if (matterSel[0].equals(general)) matterSel[0] = CREATE_NEW_CASE;
            setChosen(matterTv, matterSel[0], general);
        };
        if (multi[0]) applyMulti.run();
        // dashed "tap to choose" area (accent colour, upload icon)
        final LinearLayout drop = new LinearLayout(this);
        drop.setOrientation(LinearLayout.VERTICAL);
        drop.setGravity(Gravity.CENTER);
        drop.setPadding(dp(16), dp(22), dp(16), dp(22));
        android.graphics.drawable.GradientDrawable dash = new android.graphics.drawable.GradientDrawable();
        dash.setColor(AppTheme.field(this));
        dash.setCornerRadius(dp(14));
        dash.setStroke(dp(2), AppTheme.accent(this), dp(6), dp(4));
        drop.setBackground(dash);
        f.addView(drop, lpTop(16));

        final int[] landed = {0};
        final Runnable draw = () -> {
            drop.removeAllViews();
            if (!multi[0] && !asked[0] && dlgUris.size() > 1) {      // global detection: more than one file
                asked[0] = true;
                drop.post(() -> MultiDocDetector.confirm(this, dlgUris.size(), dlgUris.size(), () -> {
                    multi[0] = true;
                    applyMulti.run();
                    if (dlgRefresh != null) dlgRefresh.run();
                }, () -> { }));
            }
            final boolean has = !dlgUris.isEmpty();
            final int ok = AppTheme.topics(this);
            // file chosen: solid green frame + soft green fill; empty: dashed accent frame
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setCornerRadius(dp(14));
            if (has) {
                g.setColor((ok & 0x00FFFFFF) | 0x1A000000);
                g.setStroke(dp(2), ok);
            } else {
                g.setColor(AppTheme.field(this));
                g.setStroke(dp(2), AppTheme.accent(this), dp(6), dp(4));
            }
            drop.setBackground(g);
            ImageView top = CaseViews.icon(this, has ? R.drawable.ic_check_circle : R.drawable.ic_upload,
                    has ? ok : AppTheme.accent(this), has ? 40 : 34);
            drop.addView(top);
            if (has && dlgUris.size() > landed[0]) {          // a new file just landed: pop the tick
                top.setScaleX(0.3f);
                top.setScaleY(0.3f);
                top.animate().scaleX(1f).scaleY(1f).setDuration(380)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start();
                drop.setAlpha(0.6f);
                drop.animate().alpha(1f).setDuration(300).start();
                drop.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
            landed[0] = dlgUris.size();
            if (!has) {
                TextView t1 = CaseViews.text(this, "Tap to choose PDF, Word or image file", 13.5f, AppTheme.accent(this), true);
                t1.setGravity(Gravity.CENTER);
                t1.setPadding(0, dp(8), 0, dp(2));
                drop.addView(t1);
                TextView t2 = CaseViews.text(this, "Supports .pdf, .doc, .docx, .jpg, .png files, or take photos",
                        11, AppTheme.sub(this), false);
                t2.setGravity(Gravity.CENTER);
                drop.addView(t2);
            } else {
                TextView ready = CaseViews.text(this, dlgUris.size() == 1 ? "File ready" : dlgUris.size() + " files ready",
                        13.5f, ok, true);
                ready.setGravity(Gravity.CENTER);
                ready.setPadding(0, dp(6), 0, dp(4));
                drop.addView(ready);
                if (dlgUris.size() > 1) {
                    TextView orderHint = CaseViews.text(this, "Touch and hold a file, then drag it up or down to change the order",
                            11, AppTheme.sub(this), false);
                    orderHint.setGravity(Gravity.CENTER);
                    orderHint.setPadding(0, 0, 0, dp(6));
                    drop.addView(orderHint);
                }
                final int[] lead = multi[0] ? dlgLeaders() : null;
                for (int fi = 0; fi < dlgUris.size(); fi++) {
                    final int idx = fi;
                    final String fname = displayName(dlgUris.get(fi));
                    final int kind = DocFileCard.kind(fname);
                    final LinearLayout fileCard = new LinearLayout(this);
                    fileCard.setOrientation(LinearLayout.VERTICAL);
                    fileCard.setPadding(dp(10), dp(10), dp(10), dp(10));
                    fileCard.setBackground(dlgCardBg(false));
                    LinearLayout topRow = new LinearLayout(this);
                    topRow.setOrientation(LinearLayout.HORIZONTAL);
                    topRow.setGravity(Gravity.CENTER_VERTICAL);
                    ImageView thumbView = new ImageView(this);
                    thumbView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    android.graphics.drawable.GradientDrawable thumbBg = new android.graphics.drawable.GradientDrawable();
                    thumbBg.setCornerRadius(dp(10));
                    thumbBg.setColor(AppTheme.card(this));
                    thumbView.setBackground(thumbBg);
                    thumbView.setClipToOutline(true);
                    dlgThumb(thumbView, dlgUris.get(fi), kind);
                    thumbView.setContentDescription("View file");
                    thumbView.setOnClickListener(tv -> dlgView(idx));      // tap the thumbnail = View
                    TextView numView = CaseViews.text(this, String.valueOf(fi + 1), 15, AppTheme.text(this), true);
                    numView.setGravity(Gravity.CENTER);
                    numView.setPadding(0, 0, dp(8), 0);
                    topRow.addView(numView, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    topRow.addView(thumbView, new LinearLayout.LayoutParams(dp(72), dp(72)));
                    LinearLayout infoCol = new LinearLayout(this);
                    infoCol.setOrientation(LinearLayout.VERTICAL);
                    infoCol.setPadding(dp(12), 0, 0, 0);
                    String shownName = multi[0] ? dlgDocName(idx, lead) : null;
                    TextView t1 = CaseViews.text(this, shownName != null ? shownName : fname, 13.5f, AppTheme.text(this), true);
                    t1.setSingleLine(true);
                    t1.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                    infoCol.addView(t1);
                    String sz = sizeText(fileSize(dlgUris.get(fi)));
                    TextView t2 = CaseViews.text(this, DocFileCard.kindLabel(kind, fname) + (sz.isEmpty() ? "" : "  \u2022  " + sz),
                            11, AppTheme.sub(this), false);
                    t2.setPadding(0, dp(3), 0, 0);
                    infoCol.addView(t2);
                    if (multi[0]) {
                        TextView ml = dlgMetaLine(idx, lead);
                        if (ml != null) infoCol.addView(ml);
                    }
                    topRow.addView(infoCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                    if (multi[0] && lead != null && lead[idx] >= 0) {      // details set: its category sits on top of the file
                        String[] lm = dlgMeta.get(dlgUris.get(lead[idx]).toString());
                        if (lm != null && !lm[1].isEmpty()) {
                            int gold = AppTheme.isDark(this) ? 0xFFF0B03A : 0xFFB7791F;
                            TextView chip = CaseViews.text(this, lm[1].toUpperCase(Locale.ROOT), 11, gold, true);
                            chip.setSingleLine(true);
                            chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
                            chip.setPadding(dp(10), dp(4), dp(10), dp(4));
                            chip.setBackground(CaseViews.box(this, (gold & 0x00FFFFFF) | 0x22000000, 8,
                                    (gold & 0x00FFFFFF) | 0x66000000, 1));
                            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                            chipLp.bottomMargin = dp(8);
                            fileCard.addView(chip, chipLp);
                        }
                    }
                    fileCard.addView(topRow, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    LinearLayout.LayoutParams fileCardLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    fileCardLp.topMargin = dp(fi == 0 ? 4 : 10);
                    // touch and hold = pick the file up; drop it on another file to put it in that place
                    final View.OnLongClickListener pickUp = pv -> {
                        if (dlgUris.size() < 2) return false;
                        dlgDragFrom = idx;
                        pv.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        View src = fileCard;
                        src.startDragAndDrop(ClipData.newPlainText("file", String.valueOf(idx)),
                                new View.DragShadowBuilder(src), null, 0);
                        return true;
                    };
                    fileCard.setOnLongClickListener(pickUp);
                    topRow.setOnLongClickListener(pickUp);
                    fileCard.setOnDragListener((dv, ev) -> {
                        switch (ev.getAction()) {
                            case android.view.DragEvent.ACTION_DRAG_STARTED:
                                if (idx == dlgDragFrom) dv.setAlpha(0.35f);
                                return true;
                            case android.view.DragEvent.ACTION_DRAG_ENTERED:
                                if (idx != dlgDragFrom) dv.setBackground(dlgCardBg(true));
                                return true;
                            case android.view.DragEvent.ACTION_DRAG_EXITED:
                                dv.setBackground(dlgCardBg(false));
                                return true;
                            case android.view.DragEvent.ACTION_DRAG_LOCATION:
                                dlgAutoScroll(dv, ev.getY());
                                return true;
                            case android.view.DragEvent.ACTION_DROP: {
                                int from = dlgDragFrom;
                                dlgDragFrom = -1;
                                if (from >= 0 && from < dlgUris.size() && from != idx) {
                                    Uri dragged = dlgUris.remove(from);
                                    dlgUris.add(Math.min(idx, dlgUris.size()), dragged);
                                    dlgPagesReset();
                                    dv.post(() -> dlgRefresh.run());
                                }
                                return true;
                            }
                            case android.view.DragEvent.ACTION_DRAG_ENDED:
                                dv.setAlpha(1f);
                                dv.setBackground(dlgCardBg(false));
                                return true;
                            default:
                                return true;
                        }
                    });
                    drop.addView(fileCard, fileCardLp);
                    LinearLayout btns = new LinearLayout(this);
                    btns.setOrientation(LinearLayout.HORIZONTAL);
                    btns.addView(dlgBtn(R.drawable.ic_replace, AppTheme.accent(this), "Replace", () ->
                            PickerDialogs.confirm(this, "Replace file?",
                                    "\"" + displayName(dlgUris.get(idx)) + "\" will be replaced by a new file.",
                                    "Replace", () -> PickerDialogs.actions(this, "Replace file",
                                            SRC_LABELS,
                                            SRC_ICONS,
                                            i -> {
                                                dlgReplaceIdx = idx;
                                                if (i == 0) pickFile();
                                                else takePhoto();
                                            }))), dlgBtnLp(false));
                    btns.addView(dlgBtn(R.drawable.ic_delete, RoundEdit.RED, "Delete", () ->
                            PickerDialogs.confirm(this, "Delete file?",
                                    "\"" + displayName(dlgUris.get(idx)) + "\" will be removed from this upload.",
                                    "Delete", () -> {
                                        dlgUris.remove(idx);
                                        dlgPagesReset();
                                        dlgRefresh.run();
                                    })), dlgBtnLp(true));
                    if (kind == 2) {        // image: turn 90 degrees clockwise, the turned picture is what gets saved
                        View rot = dlgBtn(R.drawable.ic_rotate, AppTheme.accent(this), "", () -> dlgRotate(idx));
                        rot.setContentDescription("Rotate 90 degrees");
                        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(46),
                                ViewGroup.LayoutParams.MATCH_PARENT);
                        rlp.leftMargin = dp(8);
                        btns.addView(rot, rlp);
                    }
                    LinearLayout.LayoutParams btnsLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    btnsLp.topMargin = dp(10);
                    fileCard.addView(btns, btnsLp);
                    if (multi[0]) {         // details of this file (same fields as a Document Vault card Edit)
                        View ed = dlgBtn(R.drawable.ic_edit, AppTheme.accent(this), "Edit details", () -> dlgEditMeta(idx, matterSel[0]));
                        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        elp.topMargin = dp(8);
                        fileCard.addView(ed, elp);
                        LinearLayout nr = new LinearLayout(this);
                        nr.setOrientation(LinearLayout.HORIZONTAL);
                        String curNote = dlgNotes.get(dlgUris.get(idx).toString());
                        boolean hasNote = curNote != null && !curNote.trim().isEmpty();
                        int noteCol = hasNote ? AppTheme.isDark(this) ? 0xFFFFB74D : 0xFFE65100 : AppTheme.sub(this);
                        View rb = dlgBtn(R.drawable.ic_refresh, RoundEdit.RED, "Reset", () -> dlgResetFile(idx));
                        View nb = dlgBtn(R.drawable.ic_sticky_note, noteCol, "Notes", () -> dlgEditNote(idx));
                        if (hasNote) dlgBlink(nb);
                        nr.addView(rb, dlgBtnLp(false));
                        nr.addView(nb, dlgBtnLp(true));
                        LinearLayout.LayoutParams nrlp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        nrlp.topMargin = dp(8);
                        fileCard.addView(nr, nrlp);
                    }
                    if (kind == 0) {       // PDF: reopen it to delete pages or add photos
                        View pages = dlgBtn(R.drawable.ic_edit, AppTheme.accent(this), "Edit pages (add / remove)", () ->
                                PdfPageEditor.open(this, flow, dlgUris.get(idx), neu -> {
                                    dlgSet(idx, neu);
                                    dlgRefresh.run();
                                }));
                        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        plp.topMargin = dp(8);
                        fileCard.addView(pages, plp);
                    }
                }
                // more files / photos into the SAME group, before saving
                View more = dlgBtn(R.drawable.ic_add_circle, AppTheme.accent(this), "Add more files / photos", () ->
                        PickerDialogs.actions(this, "Add to this document", SRC_LABELS, SRC_ICONS, i -> {
                            dlgReplaceIdx = -1;
                            if (i == 0) pickFile();
                            else takePhoto();
                        }));
                LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                mlp.topMargin = dp(14);
                drop.addView(more, mlp);
            }
            // empty box: tap to choose. With files: only the View / Replace / Delete buttons act.
            if (!has) drop.setOnClickListener(v -> PickerDialogs.actions(this, "Upload file",
                    SRC_LABELS,
                    SRC_ICONS,
                    i -> {
                        if (i == 0) pickFile();
                        else takePhoto();
                    }));
            else drop.setOnClickListener(null);
            drop.setClickable(!has);
        };
        draw.run();
        dlgRefresh = () -> {
            draw.run();
            dlgSaveLook(multi[0]);
        };

        final java.util.function.BooleanSupplier[] saver = new java.util.function.BooleanSupplier[1];
        final android.app.Dialog[] dlg = new android.app.Dialog[1];
        saver[0] = () -> {
            if (!LegalUi.need(this, name, "Document name")) return false;
            if (dlgUris.isEmpty()) {
                Toast.makeText(this, "Choose a file first", Toast.LENGTH_SHORT).show();
                return false;
            }
            String base = LegalUi.str(name);
            // matter changed later in the Category marker (View > Attach to Matter > OK) applies here too
            if (fromMarker && !matterTouched[0]) {
                String live = getSharedPreferences("pdf_matter_live", MODE_PRIVATE).getString(markerPath, "");
                if (!live.isEmpty()) {
                    if (live.equals(MatterPicker.VAULT) || live.equals("General Document Vault")) matterSel[0] = general;
                    else if (live.equals("+ Create new case")) matterSel[0] = CREATE_NEW_CASE;
                    else for (DiaryStore.HCase hc : cases) {
                        String t = (!hc.first.isEmpty() && !hc.second.isEmpty()) ? hc.first + " vs. " + hc.second : hc.title();
                        if (t.equals(live) || hc.title().equals(live)) matterSel[0] = hc.title();
                    }
                }
            }
            final boolean newCase = matterSel[0].equals(CREATE_NEW_CASE);
            // a document for the case being created is kept in the Vault until the case is saved
            String matterName = matterSel[0].equals(general) || newCase ? "General Document Vault" : matterSel[0];
            String shownMatter = newCase ? "This new case" : matterName;
            if (autoCat[0]) catSel[0] = markedCategory(marks);
            // a picked file is used in place (moved on save); only a stream-only file is copied into the Documents folder
            for (Uri pu : dlgUris) {
                if (markerFiles.containsKey(pu.toString())) continue;
                File inPlace = DataFolder.localOriginal(this, pu);
                if (inPlace != null) {          // reachable file: used where it is, moved into the app folder on save
                    markerFiles.put(pu.toString(), inPlace.getAbsolutePath());
                    continue;
                }
                File docsDir = DataFolder.documentsDir(this);
                File cp = docsDir == null ? null : DataFolder.importFile(this, pu, docsDir);
                if (cp == null) {
                    Toast.makeText(this, "Could not copy this file into the app folder", Toast.LENGTH_LONG).show();
                    return false;
                }
                markerFiles.put(pu.toString(), cp.getAbsolutePath());
                DataFolder.registerOriginal(this, pu, cp);   // original removed when the document is saved
            }
            // a multi-page PDF with no marked page and one category: ask how its pages should be marked
            if (matterSel[0].equals(general) && wholeMode[0] < 0 && !catSel[0].isEmpty() && !catSel[0].equals(MULTIPLE)) {
                for (Uri gu : dlgUris) {
                    String gp = markerFiles.get(gu.toString());
                    if (gp == null || !new MarkStore(this).all(gp).isEmpty()) continue;
                    final int pc = pdfPageCount(new File(gp));
                    if (pc < 2) continue;
                    final String cat = catSel[0];
                    final Runnable again = () -> { if (saver[0].getAsBoolean() && dlg[0] != null) dlg[0].dismiss(); };
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("Mark pages")
                            .setMessage("This file has " + pc + " pages. All of them will be marked as \"" + cat
                                    + "\". Are all the pages of this category?")
                            .setPositiveButton("All pages", (d, w) -> { wholeMode[0] = 0; again.run(); })
                            .setNeutralButton("Some pages", (d, w) -> {
                                final EditText er = new RoundEdit(this);
                                FieldStyle.smallHint(er, "Pages of \"" + cat + "\", e.g. 1-4, 6");
                                er.setSingleLine(true);
                                LinearLayout bx = new LinearLayout(this);
                                bx.setPadding(dp(20), dp(8), dp(20), 0);
                                bx.addView(er, new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                                new android.app.AlertDialog.Builder(this)
                                        .setTitle("Which pages (1 to " + pc + ")?")
                                        .setMessage("The other pages stay unmarked; mark them later in the Category marker.")
                                        .setView(bx)
                                        .setPositiveButton("OK", (d2, w2) -> {
                                            if (parsePages(LegalUi.str(er), pc).isEmpty()) {
                                                Toast.makeText(this, "Enter valid page numbers", Toast.LENGTH_SHORT).show();
                                                return;
                                            }
                                            wholeRange[0] = LegalUi.str(er);
                                            wholeMode[0] = 1;
                                            again.run();
                                        })
                                        .setNegativeButton("Cancel", null)
                                        .show();
                            })
                            .setNegativeButton("Mark later", (d, w) -> { wholeMode[0] = 2; again.run(); })
                            .show();
                    return false;
                }
            }
            LegalStore ls = new LegalStore(this);
            LegalStore.Doc dup = matterSel[0].equals(general) || newCase || catSel[0].equals(MULTIPLE) ? null
                    : sameCategoryDoc(ls, cases, matterSel[0], catSel[0]);
            if (dup != null && !dupOk[0]) {
                PickerDialogs.confirm(this, "Already uploaded",
                        "\"" + dup.name + "\" (" + dup.date + ") is already saved under category \""
                                + catSel[0] + "\" for " + matterName + ". Upload another file in the same category?",
                        "Upload anyway", () -> {
                            dupOk[0] = true;
                            if (saver[0].getAsBoolean() && dlg[0] != null) dlg[0].dismiss();
                        });
                return false;
            }
            dupOk[0] = false;
            // all files chosen in this dialog belong to ONE group: one card, one Vault entry
            List<Uri> group = new ArrayList<>(dlgUris);
            final String[] groupNotes = new String[group.size()];
            for (int ni = 0; ni < group.size(); ni++) groupNotes[ni] = dlgNotes.get(group.get(ni).toString());
            // a file saved from the marker leaves its original place and lives in the app's folder from now on
            for (int gi = 0; gi < group.size(); gi++) {
                String oldPath = markerFiles.get(group.get(gi).toString());
                if (oldPath == null) continue;
                File moved = DataFolder.moveIntoDocuments(this, new File(oldPath));
                if (moved == null) continue;
                new MarkStore(this).move(this, oldPath, moved.getAbsolutePath());
                // no page marked yet: mark by the choice made above (all pages / listed pages / later)
                MarkStore ms = new MarkStore(this);
                String wholeCat = catSel[0];
                String mp2 = moved.getAbsolutePath();
                if (matterSel[0].equals(general) && ms.all(mp2).isEmpty()
                        && !wholeCat.isEmpty() && !wholeCat.equals(MULTIPLE)) {
                    if (wholeMode[0] == 2) {
                        // marked later by the user: the one-time auto repair must not mark it
                        getSharedPreferences("vault_repaired", MODE_PRIVATE).edit().putBoolean(mp2, true).apply();
                    } else {
                        int pc = DocFileCard.kind(moved.getName()) == 1 || DocFileCard.kind(moved.getName()) == 2
                                ? 1 : pdfPageCount(moved);   // an image or a Word file is one unit = page 1
                        java.util.Set<Integer> only = wholeMode[0] == 1 ? parsePages(wholeRange[0], pc) : null;
                        for (int pg = 0; pg < pc; pg++) {
                            if (only == null || only.contains(pg)) ms.set(mp2, pg, wholeCat);
                        }
                    }
                }
                Uri nu = PhotoFlow.uri(this, moved);
                markerFiles.remove(group.get(gi).toString());
                markerFiles.put(nu.toString(), moved.getAbsolutePath());
                group.set(gi, nu);
            }
            for (int ni = 0; ni < group.size(); ni++) {      // file notes go where the Document Vault file row reads them
                String np = markerFiles.get(group.get(ni).toString());
                if (np != null && groupNotes[ni] != null && !groupNotes[ni].isEmpty())
                    getSharedPreferences("doc_notes", MODE_PRIVATE).edit().putString("file:" + np, groupNotes[ni]).apply();
            }
            Uri first = group.get(0);
            List<Uri> rest = new ArrayList<>(group.subList(1, group.size()));
            String shown = base + extOf(displayName(first));
            String today = new SimpleDateFormat("dd MMM yyyy", Locale.US).format(new Date());
            String total = FileSetDialog.totalSize(this, group);
            String fname = FileSetDialog.label(this, group);
            pickedUris.add(first);
            pickedMore.add(rest);
            pickedNames.add(shown);
            pickedMeta.add(new String[]{catSel[0], shownMatter, stageSel[0], total, today, fname});
            LegalStore.Doc d = new LegalStore.Doc();
            d.name = shown;
            d.caseName = matterName;
            d.category = catSel[0];
            d.stage = stageSel[0];
            d.uri = first.toString();
            for (Uri r : rest) d.more.add(r.toString());
            d.fileName = fname;
            d.size = total;
            d.date = today;
            ls.saveDoc(d);
            if (newCase) pendingNewCaseDocs.add(d.uri);
            if (matterSel[0].equals(general)) {
                String lead = markerFiles.get(first.toString());
                vaultCardPath = lead == null ? "" : lead;
                mirrorNoteToVaultCard();            // Notes already typed on this page belong to the card just saved
            }
            if (matterSel[0].equals(general)) { vaultSaved = true; missing(); relaxRequiredLabels(findViewById(android.R.id.content)); }
            dlgUris.clear();
            renderFiles();
            refresh();
            return true;
        };
        final java.util.function.BooleanSupplier coreSave = saver[0];
        // multi mode: one document per details set; the files named in its page range join it
        final java.util.function.BooleanSupplier multiSave = () -> {
            if (!multi[0]) return coreSave.getAsBoolean();
            if (dlgUris.isEmpty()) {
                Toast.makeText(this, "Choose a file first", Toast.LENGTH_SHORT).show();
                return false;
            }
            int[] lead = dlgLeaders();
            int doneFiles = 0;
            for (int l : lead) if (l >= 0) doneFiles++;
            for (int i = 0; i < lead.length; i++) {
                if (lead[i] < 0) {          // every file must be marked before anything is saved
                    Toast.makeText(this, "Set details for file " + (i + 1) + " first (" + doneFiles + " of "
                            + lead.length + " files done)", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
            final List<Uri> all = new ArrayList<>(dlgUris);
            java.util.LinkedHashMap<Integer, List<Uri>> groups = new java.util.LinkedHashMap<>();
            for (int i = 0; i < all.size(); i++) {
                List<Uri> g = groups.get(lead[i]);
                if (g == null) { g = new ArrayList<>(); groups.put(lead[i], g); }
                g.add(all.get(i));
            }
            wholeMode[0] = 0;          // pages of a multi-page PDF: all marked with that document's category
            dupOk[0] = true;
            matterTouched[0] = true;
            autoCat[0] = false;
            List<Uri> left = new ArrayList<>(all);
            for (java.util.Map.Entry<Integer, List<Uri>> ge : groups.entrySet()) {
                String[] m = dlgMeta.get(all.get(ge.getKey()).toString());
                name.setText(m[0]);
                catSel[0] = m[1];
                stageSel[0] = m[2];
                matterSel[0] = m[3];
                dlgUris.clear();
                dlgUris.addAll(ge.getValue());
                if (!coreSave.getAsBoolean()) {
                    dlgUris.clear();
                    dlgUris.addAll(left);       // what is not saved yet stays in the box
                    if (dlgRefresh != null) dlgRefresh.run();
                    return false;
                }
                left.removeAll(ge.getValue());
            }
            dlgMeta.clear();
        dlgNotes.clear();
            return true;
        };
        dlg[0] = PickerDialogs.formSheet(this, "Upload Legal Document",
                "Select PDF, Word or image file to store in the Document Vault.", f, "Save to Vault",
                () -> multiSave.getAsBoolean(), () -> { dlgRefresh = null; dlgSheet = null; },
                () -> PickerDialogs.confirm(this, "Discard upload?",
                        "Attached files and details entered here will be discarded.", "Discard", () -> {
                            dlgUris.clear();
                            if (dlg[0] != null) dlg[0].dismiss();
                            finish();       // back to the screen it came from (Document Vault)
                        }));
        dlgSheet = dlg[0];
        dlgSaveLook(multi[0]);
        if (fromMarker && !multi[0]) {       // arriving from the Category marker: cursor straight in Document Name
            name.requestFocus();
            if (dlg[0] != null && dlg[0].getWindow() != null) {
                dlg[0].getWindow().setSoftInputMode(
                        android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                                | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            name.postDelayed(() -> {
                name.requestFocus();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                        getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(name, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }, 250);
        }
    }

    /**
     * An earlier upload with the same Document Category for this matter, when the matter is a saved case
     * that belongs to a person (client). Null for the General Document Vault or a case with no client.
     */
    private LegalStore.Doc sameCategoryDoc(LegalStore ls, List<DiaryStore.HCase> cases, String matter, String cat) {
        boolean person = false;
        for (DiaryStore.HCase hc : cases) {
            if (hc.title().equals(matter) && !hc.clientId.isEmpty()) person = true;
        }
        if (!person) return null;
        LegalStore.Doc hit = null;
        for (LegalStore.Doc d : ls.docs()) {
            if (d.caseName.equals(matter) && d.category.equalsIgnoreCase(cat)) { hit = d; break; }   // newest first
        }
        return hit;
    }

    private static int pdfPageCount(File f) {
        try (android.os.ParcelFileDescriptor pf = android.os.ParcelFileDescriptor.open(f,
                android.os.ParcelFileDescriptor.MODE_READ_ONLY);
             android.graphics.pdf.PdfRenderer pr = new android.graphics.pdf.PdfRenderer(pf)) {
            return pr.getPageCount();
        } catch (Exception e) {
            return 0;
        }
    }

    /** "1-4, 6" -> 0-based page indexes within 1..max; anything out of range is ignored. */
    private static java.util.Set<Integer> parsePages(String text, int max) {
        java.util.Set<Integer> out = new java.util.TreeSet<>();
        for (String part : text.split("[,;\\s]+")) {
            if (part.isEmpty()) continue;
            try {
                int a, b;
                if (part.contains("-")) {
                    String[] r = part.split("-");
                    a = Integer.parseInt(r[0].trim());
                    b = Integer.parseInt(r[1].trim());
                } else {
                    a = b = Integer.parseInt(part.trim());
                }
                for (int i = Math.max(1, Math.min(a, b)); i <= Math.min(max, Math.max(a, b)); i++) out.add(i - 1);
            } catch (Exception ignored) { }
        }
        return out;
    }

    private int dlgDragFrom = -1;
    private final java.util.Map<String, android.graphics.Bitmap> dlgThumbs = new java.util.HashMap<>();

    private android.graphics.drawable.GradientDrawable dlgCardBg(boolean hot) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(hot ? (AppTheme.accent(this) & 0x00FFFFFF) | 0x22000000 : 0x00000000);
        g.setStroke(dp(hot ? 2 : 1), hot ? AppTheme.accent(this) : AppTheme.cardStroke(this));
        return g;
    }

    /** While a file is being dragged: scroll the dialog when the finger is near its top or bottom edge. */
    private void dlgAutoScroll(View card, float yInCard) {
        android.view.ViewParent p = card.getParent();
        while (p != null && !(p instanceof ScrollView)) p = p.getParent();
        if (p == null) return;
        ScrollView sc = (ScrollView) p;
        int[] a = new int[2];
        int[] b = new int[2];
        card.getLocationOnScreen(a);
        sc.getLocationOnScreen(b);
        float rel = a[1] + yInCard - b[1];
        if (rel < dp(80)) sc.scrollBy(0, -dp(16));
        else if (rel > sc.getHeight() - dp(80)) sc.scrollBy(0, dp(16));
    }

    /** Small preview of a chosen file: the picture itself, the first page of a PDF, or the file-type icon. */
    private void dlgThumb(final ImageView iv, final Uri u, final int kind) {
        final String key = u.toString();
        android.graphics.Bitmap cached = dlgThumbs.get(key);
        if (cached != null) {
            iv.setImageBitmap(cached);
            return;
        }
        iv.setPadding(dp(20), dp(20), dp(20), dp(20));
        iv.setImageResource(DocFileCard.kindIcon(kind));
        iv.setColorFilter(DocFileCard.kindColor(this, kind), android.graphics.PorterDuff.Mode.SRC_IN);
        if (kind != 0 && kind != 2) return;
        new Thread(() -> {
            android.graphics.Bitmap bmp = null;
            try {
                if (kind == 2) {
                    android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    try (java.io.InputStream is = getContentResolver().openInputStream(u)) {
                        android.graphics.BitmapFactory.decodeStream(is, null, o);
                    }
                    int sample = 1;
                    while (o.outWidth / (sample * 2) >= 260 && o.outHeight / (sample * 2) >= 260) sample *= 2;
                    android.graphics.BitmapFactory.Options o2 = new android.graphics.BitmapFactory.Options();
                    o2.inSampleSize = sample;
                    try (java.io.InputStream is = getContentResolver().openInputStream(u)) {
                        bmp = android.graphics.BitmapFactory.decodeStream(is, null, o2);
                    }
                    int rot = 0;
                    try (java.io.InputStream is = getContentResolver().openInputStream(u)) {
                        int ori = new android.media.ExifInterface(is).getAttributeInt(
                                android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL);
                        rot = ori == android.media.ExifInterface.ORIENTATION_ROTATE_90 ? 90
                                : ori == android.media.ExifInterface.ORIENTATION_ROTATE_180 ? 180
                                : ori == android.media.ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
                    } catch (Exception ignored) { }
                    if (bmp != null && rot != 0) {
                        android.graphics.Matrix m = new android.graphics.Matrix();
                        m.postRotate(rot);
                        bmp = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                    }
                } else {
                    try (android.os.ParcelFileDescriptor pf = getContentResolver().openFileDescriptor(u, "r");
                         android.graphics.pdf.PdfRenderer pr = new android.graphics.pdf.PdfRenderer(pf)) {
                        if (pr.getPageCount() > 0) {
                            android.graphics.pdf.PdfRenderer.Page pg = pr.openPage(0);
                            int w = 260;
                            int h = Math.max(1, w * pg.getHeight() / Math.max(1, pg.getWidth()));
                            bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
                            bmp.eraseColor(0xFFFFFFFF);
                            pg.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                            pg.close();
                        }
                    }
                }
            } catch (Throwable ignored) {
                bmp = null;
            }
            final android.graphics.Bitmap done = bmp;
            if (done == null) return;
            runOnUiThread(() -> {
                dlgThumbs.put(key, done);
                iv.setPadding(0, 0, 0, 0);
                iv.clearColorFilter();
                iv.setImageBitmap(done);
            });
        }, "dlg-thumb").start();
    }

    /** Small labelled button (icon + text) used under a chosen file in the upload dialog. */
    private View dlgBtn(int icon, int color, String label, Runnable click) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(4), dp(5), dp(4), dp(5));
        b.setBackground(CaseViews.box(this, AppTheme.field(this), 10, color, 1));
        b.addView(CaseViews.icon(this, icon, color, 14));
        if (!label.isEmpty()) {
            TextView t = CaseViews.text(this, label, 11, color, true);
            t.setSingleLine(true);
            t.setEllipsize(null);
            t.setPadding(dp(3), 0, 0, 0);
            b.addView(t);
        }
        b.setOnClickListener(v -> click.run());
        return b;
    }

    private LinearLayout.LayoutParams dlgBtnLp(boolean gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (gap) lp.leftMargin = dp(8);
        return lp;
    }

    /** Label with the New Case look (12sp bold grey, scaled 0.9 like every label on that page). */
    private TextView dlgLbl(String s) {
        return CaseViews.fieldLabel(this, s);
    }

    /** Ask: PDF from the phone, or a camera photo. replaceAt >= 0 swaps that file instead of adding. */
    private void chooseSource(int replaceAt) {
        replaceIdx = replaceAt;
        PickerDialogs.actions(this, replaceAt >= 0 ? "Replace file" : "Upload file",
                SRC_LABELS,
                SRC_ICONS,
                i -> {
                    if (i == 0) pickFile();
                    else takePhoto();
                });
    }

    /** Camera: any number of photos in a row (each can be deleted), then the user picks PDF or image. */
    private void takePhoto() {
        flow.start(true, new PhotoFlow.Result() {
            @Override public void images(List<File> files) {
                List<Uri> u = new ArrayList<>();
                for (File f : files) u.add(PhotoFlow.uri(NewCaseActivity.this, f));
                deliverAll(u);
            }
            @Override public void pdf(File file) {
                deliverAll(java.util.Collections.singletonList(PhotoFlow.uri(NewCaseActivity.this, file)));
            }
            @Override public void cancelled() {
                dlgReplaceIdx = -1;
            }
        });
    }

    private void deliverAll(List<Uri> uris) {
        if (dlgRefresh != null && replaceIdx < 0) {
            for (Uri u : uris) dlgAdd(u);
            dlgRefresh.run();
        } else {
            for (Uri u : uris) addPicked(u);
            renderFiles();
        }
    }

    /** After a PDF was edited (pages removed / photos added): keep the Vault copy pointing at the new file. */
    private void updateStoredDoc(Uri old, Uri neu) {
        int pend = pendingNewCaseDocs.indexOf(old.toString());
        if (pend >= 0) pendingNewCaseDocs.set(pend, neu.toString());
        LegalStore ls = new LegalStore(this);
        for (LegalStore.Doc d : ls.docs()) {
            if (old.toString().equals(d.uri)) {
                d.uri = neu.toString();
                d.fileName = PdfTools.fileName(this, neu);
                d.size = PdfTools.sizeLabel(this, neu);
                ls.saveDoc(d);
                break;
            }
        }
    }

    private void openFile(int idx) {
        openUri(pickedUris.get(idx));
    }

    private void openUri(Uri u) {
        String markedPath = markerFiles.get(u.toString());
        if (markedPath != null && markedPath.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")
                && new java.io.File(markedPath).isFile()) {      // a marked PDF: open the Category marker
            Intent mv = new Intent(this, MarkActivity.class);
            mv.putExtra("path", markedPath);
            mv.putExtra("from_new_case", true);
            startActivity(mv);
            return;
        }
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

    /** Full-screen viewer over the files attached in the upload dialog: swipe or arrows go to the previous / next file. */
    private java.util.function.BooleanSupplier dlgMultiNow = () -> false;
    private java.util.function.Supplier<String> dlgMatterNow = () -> "";

    private void dlgView(int start) {
        if (dlgUris.isEmpty()) return;
        final int[] cur = {Math.max(0, Math.min(start, dlgUris.size() - 1))};
        final int[] gen = {0};
        final android.app.Dialog vd = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final android.widget.FrameLayout body = new android.widget.FrameLayout(this);
        final TextView title = CaseViews.text(this, "", 14, 0xFFFFFFFF, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        final TextView markChip = CaseViews.text(this, "", 12, 0xFFF0B03A, true);
        markChip.setPadding(dp(12), dp(5), dp(12), dp(5));
        markChip.setBackground(CaseViews.box(this, 0xCC000000, 8, 0xFFF0B03A, 1));
        markChip.setVisibility(View.GONE);
        final TextView editBtn = CaseViews.text(this, "\u270E  Edit details", 14, 0xFFFFFFFF, true);
        editBtn.setPadding(dp(18), dp(10), dp(18), dp(10));
        editBtn.setBackground(CaseViews.box(this, 0xCC1565C0, 22, 0xFFFFFFFF, 1));
        editBtn.setVisibility(View.GONE);
        final TextView prev = CaseViews.text(this, "\u2039", 34, 0xFFFFFFFF, true);
        final TextView next = CaseViews.text(this, "\u203A", 34, 0xFFFFFFFF, true);
        final Runnable[] show = new Runnable[1];
        final java.util.function.IntConsumer go = delta -> {
            int n = cur[0] + delta;
            if (n < 0 || n >= dlgUris.size()) return;
            cur[0] = n;
            show[0].run();
        };
        for (TextView ar : new TextView[]{prev, next}) {
            ar.setGravity(Gravity.CENTER);
            ar.setBackground(CaseViews.box(this, 0x66000000, 24, 0, 0));
        }
        markChip.setOnClickListener(v -> {      // tap the category text = edit this file's details again
            if (dlgMultiNow.getAsBoolean()) dlgEditMeta(cur[0], dlgMatterNow.get(), () -> show[0].run());
        });
        editBtn.setOnClickListener(v -> dlgEditMeta(cur[0], dlgMatterNow.get(), () -> show[0].run()));
        prev.setOnClickListener(v -> go.accept(-1));
        next.setOnClickListener(v -> go.accept(1));
        show[0] = () -> {
            if (cur[0] >= dlgUris.size()) { vd.dismiss(); return; }
            final int my = ++gen[0];
            final Uri u = dlgUris.get(cur[0]);
            final String fname = displayName(u);
            final int kind = DocFileCard.kind(fname);
            final int[] vLead = dlgLeaders();
            final String vDoc = dlgDocName(cur[0], vLead);
            final String[] vm = dlgLeadMeta(cur[0]);
            title.setText((cur[0] + 1) + " / " + dlgUris.size() + "   " + (vDoc != null ? vDoc : fname));
            if (vm != null && !vm[1].isEmpty()) {
                markChip.setText(vm[1].toUpperCase(Locale.ROOT) + (vm[2].isEmpty() ? "" : "  \u2022  " + vm[2]) + "  \u270E");
                markChip.setVisibility(View.VISIBLE);
            } else markChip.setVisibility(View.GONE);
            // no details yet for this file: small Edit button opens the details box right over this image
            editBtn.setVisibility(dlgMultiNow.getAsBoolean() && vLead[cur[0]] < 0 ? View.VISIBLE : View.GONE);
            prev.setVisibility(cur[0] > 0 ? View.VISIBLE : View.INVISIBLE);
            next.setVisibility(cur[0] < dlgUris.size() - 1 ? View.VISIBLE : View.INVISIBLE);
            body.removeAllViews();
            if (kind != 0 && kind != 2) {       // Word etc: cannot draw here, hand over to another app
                TextView open = CaseViews.text(this, "Open in another app", 15, 0xFFFFFFFF, true);
                open.setPadding(dp(24), dp(14), dp(24), dp(14));
                open.setBackground(CaseViews.box(this, 0x33FFFFFF, 12, 0xFFFFFFFF, 1));
                open.setOnClickListener(v -> openUri(u));
                body.addView(open, new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
                return;
            }
            final android.widget.ScrollView sv = new android.widget.ScrollView(this);
            final LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.setPadding(0, dp(56), 0, dp(16));
            sv.addView(col, new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            body.addView(sv, new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            final int w = Math.min(1000, getResources().getDisplayMetrics().widthPixels);
            new Thread(() -> {
                try {
                    if (kind == 2) {
                        final android.graphics.Bitmap bm = dlgDecode(u, w);
                        if (bm != null) runOnUiThread(() -> dlgViewAdd(col, bm, vd, gen, my));
                    } else {
                        try (android.os.ParcelFileDescriptor pf = getContentResolver().openFileDescriptor(u, "r");
                             android.graphics.pdf.PdfRenderer pr = new android.graphics.pdf.PdfRenderer(pf)) {
                            for (int pi = 0; pi < pr.getPageCount() && gen[0] == my; pi++) {
                                android.graphics.pdf.PdfRenderer.Page pg = pr.openPage(pi);
                                int h = Math.max(1, w * pg.getHeight() / Math.max(1, pg.getWidth()));
                                final android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(
                                        w, h, android.graphics.Bitmap.Config.ARGB_8888);
                                bm.eraseColor(0xFFFFFFFF);
                                pg.render(bm, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                                pg.close();
                                runOnUiThread(() -> dlgViewAdd(col, bm, vd, gen, my));
                            }
                        }
                    }
                } catch (Throwable ignored) { }
            }, "dlg-view").start();
        };
        final android.view.GestureDetector gd = new android.view.GestureDetector(this,
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onFling(android.view.MotionEvent e1, android.view.MotionEvent e2, float vx, float vy) {
                        if (e1 == null || e2 == null) return false;
                        float dx = e2.getX() - e1.getX(), dy = e2.getY() - e1.getY();
                        if (Math.abs(dx) > dp(80) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                            go.accept(dx < 0 ? 1 : -1);
                            return true;
                        }
                        return false;
                    }
                });
        android.widget.FrameLayout root = new android.widget.FrameLayout(this) {
            @Override public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
                gd.onTouchEvent(ev);
                return super.dispatchTouchEvent(ev);
            }
        };
        root.setBackgroundColor(0xFF000000);
        root.addView(body, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xCC000000);
        bar.setPadding(dp(16), dp(10), dp(4), dp(10));
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = CaseViews.text(this, "\u2715", 20, 0xFFFFFFFF, true);
        close.setPadding(dp(16), dp(6), dp(16), dp(6));
        close.setOnClickListener(v -> vd.dismiss());
        bar.addView(close);
        root.addView(bar, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        android.widget.FrameLayout.LayoutParams mcl = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        mcl.leftMargin = dp(14);
        mcl.bottomMargin = dp(18);
        root.addView(markChip, mcl);
        android.widget.FrameLayout.LayoutParams ebl = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        ebl.rightMargin = dp(14);
        ebl.bottomMargin = dp(18);
        root.addView(editBtn, ebl);
        android.widget.FrameLayout.LayoutParams pl = new android.widget.FrameLayout.LayoutParams(dp(44), dp(64),
                Gravity.START | Gravity.CENTER_VERTICAL);
        pl.leftMargin = dp(6);
        root.addView(prev, pl);
        android.widget.FrameLayout.LayoutParams nl = new android.widget.FrameLayout.LayoutParams(dp(44), dp(64),
                Gravity.END | Gravity.CENTER_VERTICAL);
        nl.rightMargin = dp(6);
        root.addView(next, nl);
        vd.setContentView(root);
        vd.setOnDismissListener(x -> gen[0]++);
        vd.show();
        show[0].run();
    }

    /** Multi mode: Save to Vault looks off until every file has its details. */
    private void dlgSaveLook(boolean multiOn) {
        if (dlgSheet == null || dlgSheet.getWindow() == null) return;
        boolean locked = false;
        if (multiOn) {
            int[] lead = dlgLeaders();
            locked = lead.length == 0;
            for (int l : lead) if (l < 0) locked = true;
        }
        TextView sv = dlgFindText(dlgSheet.getWindow().getDecorView(), "Save to Vault");
        if (sv != null) sv.setAlpha(locked ? 0.4f : 1f);
    }

    private static TextView dlgFindText(View v, String text) {
        if (v instanceof TextView && text.contentEquals(((TextView) v).getText())) return (TextView) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView r = dlgFindText(g.getChildAt(i), text);
                if (r != null) return r;
            }
        }
        return null;
    }

    /** A file's uri changes (replace / rotate / page edit): its details stay with it. */
    private void dlgSet(int idx, Uri nu) {
        Uri old = dlgUris.get(idx);
        String[] m = dlgMeta.remove(old.toString());
        String nt = dlgNotes.remove(old.toString());
        if (nt != null) dlgNotes.put(nu.toString(), nt);
        dlgUris.set(idx, nu);
        if (m != null) dlgMeta.put(nu.toString(), m);
    }

    /** File order / count changed: page ranges (file numbers) would point at the wrong files, so they go back to "own number". */
    private void dlgPagesReset() {
        for (String[] m : dlgMeta.values()) m[4] = "";
    }

    /**
     * For each file: the index of the file whose details it uses. Itself when it has its own details, the
     * earlier file that lists its number in Page / page range, or -1 when nothing is set yet.
     */
    private int[] dlgLeaders() {
        int n = dlgUris.size();
        int[] lead = new int[n];
        java.util.Arrays.fill(lead, -1);
        for (int i = 0; i < n; i++) {
            if (lead[i] != -1) continue;
            String[] m = dlgMeta.get(dlgUris.get(i).toString());
            if (m == null || m[0].isEmpty()) continue;
            lead[i] = i;
            String pg = m[4].isEmpty() ? String.valueOf(i + 1) : m[4];
            for (int j : parsePages(pg, n)) {
                if (lead[j] == -1) lead[j] = i;
            }
        }
        return lead;
    }

    /** Pulses a view (fade out / in, forever) while it is on screen. */
    private static void dlgBlink(final View v) {
        final android.animation.ObjectAnimator an = android.animation.ObjectAnimator.ofFloat(v, View.ALPHA, 1f, 0.2f);
        an.setDuration(650);
        an.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        an.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        v.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View x) { if (!an.isStarted()) an.start(); }
            @Override public void onViewDetachedFromWindow(View x) { an.cancel(); x.setAlpha(1f); }
        });
    }

    /** Reset one file: its details, page range, note and any file whose pages pointed at it go back to "not set". */
    private void dlgResetFile(final int idx) {
        if (idx < 0 || idx >= dlgUris.size()) return;
        PickerDialogs.confirm(this, "Reset file " + (idx + 1) + "?",
                "Document name, category, stage, page range and notes of this file will be cleared.", "Reset", () -> {
                    int[] lead = dlgLeaders();
                    int owner = lead[idx] >= 0 ? lead[idx] : idx;     // a file listed in another's pages resets that owner
                    dlgMeta.remove(dlgUris.get(owner).toString());
                    dlgNotes.remove(dlgUris.get(idx).toString());
                    if (dlgRefresh != null) dlgRefresh.run();
                });
    }

    /** Note for one file of the upload box. */
    private void dlgEditNote(final int idx) {
        if (idx < 0 || idx >= dlgUris.size()) return;
        final String key = dlgUris.get(idx).toString();
        final EditText et = new EditText(this);
        et.setText(dlgNotes.getOrDefault(key, ""));
        et.setHint("Notes");
        et.setMinLines(3);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        et.setTextColor(AppTheme.text(this));
        et.setHintTextColor(AppTheme.sub(this));
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(4), dp(20), 0);
        box.addView(et, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        new android.app.AlertDialog.Builder(this)
                .setTitle("Notes: file " + (idx + 1))
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String t = et.getText().toString().trim();
                    if (t.isEmpty()) dlgNotes.remove(key); else dlgNotes.put(key, t);
                    if (dlgRefresh != null) dlgRefresh.run();
                })
                .show();
    }

    /** Document name shown for a file: its details name; "<name> page N" when several files share one document. */
    private String dlgDocName(int idx, int[] lead) {
        if (lead == null || idx < 0 || idx >= lead.length || lead[idx] < 0) return null;
        String[] m = dlgMeta.get(dlgUris.get(lead[idx]).toString());
        if (m == null || m[0].isEmpty()) return null;
        int total = 0, pos = 0;
        for (int i = 0; i < lead.length; i++) {
            if (lead[i] == lead[idx]) {
                total++;
                if (i < idx) pos++;
            }
        }
        return total > 1 ? m[0] + " page " + (pos + 1) : m[0];
    }

    /** Details (name, category, stage, matter, pages) that apply to a file: its own, or its leader's. */
    private String[] dlgLeadMeta(int idx) {
        int[] lead = dlgLeaders();
        if (idx < 0 || idx >= lead.length || lead[idx] < 0) return null;
        return dlgMeta.get(dlgUris.get(lead[idx]).toString());
    }

    private TextView dlgMetaLine(int idx, int[] lead) {
        String[] m = dlgMeta.get(dlgUris.get(idx).toString());
        String text;
        int color;
        if (lead[idx] >= 0) {
            return null;            // details set: the name and category chip already show it
        } else {
            text = "Details not set";
            color = RoundEdit.RED;
        }
        TextView t = CaseViews.text(this, text, 11.5f, color, true);
        t.setPadding(0, dp(3), 0, 0);
        return t;
    }

    /** Details of one file: Document name, Category, Stage, Attach to matter, plus Page / page range (file numbers). */
    private void dlgEditMeta(final int idx, final String defMatter) { dlgEditMeta(idx, defMatter, null); }

    private void dlgEditMeta(final int idx, final String defMatter, final Runnable after) {
        if (idx < 0 || idx >= dlgUris.size()) return;
        final String key = dlgUris.get(idx).toString();
        final int[] ld = dlgLeaders();
        final String[] own = dlgMeta.get(key);
        final String[] cur;
        if (own != null) cur = own.clone();
        else if (ld[idx] >= 0 && dlgMeta.get(dlgUris.get(ld[idx]).toString()) != null) {
            cur = dlgMeta.get(dlgUris.get(ld[idx]).toString()).clone();     // listed in another file's pages: same details
            String auto = dlgDocName(idx, ld);
            if (auto != null) cur[0] = auto;
            cur[4] = String.valueOf(idx + 1);
        } else cur = new String[]{"", "Other", "", defMatter == null || defMatter.isEmpty() ? CREATE_NEW_CASE : defMatter, ""};
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);

        f.addView(dlgLbl("Document Name / Caption *"));
        final EditText nm = new RoundEdit(this);
        FieldStyle.smallHint(nm, "e.g. Interim Injunction Application");
        nm.setSingleLine(true);
        nm.setText(cur[0]);
        f.addView(nm, lpTop(0));

        f.addView(dlgLbl("Document Category"));
        final TextView catTv = dropdown(f, "Select category", null);
        setChosen(catTv, cur[1], "Select category");
        ((View) catTv.getTag()).setOnClickListener(v -> CategoryPicker.pick(this, cur[1], c -> {
            cur[1] = c;
            setChosen(catTv, c, "Select category");
        }));

        f.addView(dlgLbl("Stage"));
        final TextView stageTv = dropdown(f, "Select stage", null);
        setChosen(stageTv, cur[2], "Select stage");
        final StageStore docStages = new StageStore(this);
        ((View) stageTv.getTag()).setOnClickListener(v ->
                PickerDialogs.stageSheet(this, "Stage", () -> docStages.list(), cur[2],
                        s -> {
                            cur[2] = s;
                            setChosen(stageTv, s, "Select stage");
                        },
                        s -> docStages.add(s),
                        (old, neu) -> {
                            docStages.rename(old, neu);
                            if (old.equals(cur[2])) {
                                cur[2] = neu;
                                setChosen(stageTv, neu, "Select stage");
                            }
                        },
                        gone -> {
                            docStages.delete(gone);
                            if (gone.equals(cur[2])) {
                                cur[2] = "";
                                setChosen(stageTv, "", "Select stage");
                            }
                        },
                        (i, j) -> docStages.swap(i, j)));

        f.addView(dlgLbl("Page / page range (file numbers of this document)"));
        final EditText pg = new RoundEdit(this);
        FieldStyle.smallHint(pg, "e.g. 1-3, 5");
        pg.setSingleLine(true);
        pg.setText(cur[4].isEmpty() ? String.valueOf(idx + 1) : cur[4]);
        f.addView(pg, lpTop(0));
        TextView hint = CaseViews.text(this,
                "The files listed here are saved together as this one document, with these details.",
                11, AppTheme.sub(this), false);
        hint.setPadding(0, dp(4), 0, 0);
        f.addView(hint);

        PickerDialogs.formSheet(this, "Document details",
                "File " + (idx + 1) + " of " + dlgUris.size() + ": " + displayName(dlgUris.get(idx)), f, "OK", () -> {
                    if (!LegalUi.need(this, nm, "Document name")) return false;
                    String ptxt = LegalUi.str(pg).trim();
                    if (parsePages(ptxt, dlgUris.size()).isEmpty()) {
                        Toast.makeText(this, "Enter valid file numbers, e.g. 1-3, 5", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    cur[0] = LegalUi.str(nm);
                    cur[4] = ptxt;
                    dlgMeta.put(key, cur);
                    if (dlgRefresh != null) dlgRefresh.run();
                    if (after != null) after.run();
                    return true;
                }, () -> { });
    }

    /** Turn the image of an upload-dialog file 90 degrees clockwise and keep it turned (re-encoded, no EXIF tag left). */
    private void dlgRotate(final int idx) {
        if (idx < 0 || idx >= dlgUris.size()) return;
        final Uri old = dlgUris.get(idx);
        final String oldName = displayName(old);
        final String markedPath = markerFiles.get(old.toString());
        new Thread(() -> {
            File out = null;
            try {
                android.graphics.Bitmap bm = dlgDecode(old, Integer.MAX_VALUE);
                if (bm == null) throw new java.io.IOException("decode");
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.postRotate(90);
                android.graphics.Bitmap r = android.graphics.Bitmap.createBitmap(
                        bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
                if (markedPath != null && new File(markedPath).isFile()) {
                    out = new File(markedPath);                 // a file the app already owns: turned in place
                } else {
                    File dir = new File(getCacheDir(), "rotated");
                    dir.mkdirs();
                    int dot = oldName.lastIndexOf('.');
                    String base = dot > 0 ? oldName.substring(0, dot) : oldName;
                    boolean png = oldName.toLowerCase(Locale.ROOT).endsWith(".png");
                    out = new File(dir, base + (png ? ".png" : ".jpg"));
                }
                boolean png = out.getName().toLowerCase(Locale.ROOT).endsWith(".png");
                File tmp = new File(out.getParentFile(), out.getName() + ".tmp");
                try (java.io.FileOutputStream fo = new java.io.FileOutputStream(tmp)) {
                    if (!r.compress(png ? android.graphics.Bitmap.CompressFormat.PNG
                            : android.graphics.Bitmap.CompressFormat.JPEG, 95, fo)) throw new java.io.IOException("encode");
                }
                if (out.exists() && !out.delete()) throw new java.io.IOException("replace");
                if (!tmp.renameTo(out)) throw new java.io.IOException("rename");
                final File done = out;
                runOnUiThread(() -> {
                    Uri nu = PhotoFlow.uri(this, done);
                    dlgThumbs.remove(old.toString());
                    dlgThumbs.remove(nu.toString());
                    if (idx < dlgUris.size() && dlgUris.get(idx).equals(old)) dlgSet(idx, nu);
                    if (markedPath != null && !nu.equals(old)) {
                        markerFiles.remove(old.toString());
                        markerFiles.put(nu.toString(), markedPath);
                    }
                    if (dlgRefresh != null) dlgRefresh.run();
                });
            } catch (Throwable e) {
                runOnUiThread(() -> Toast.makeText(this, "Could not rotate this image", Toast.LENGTH_SHORT).show());
            }
        }, "dlg-rotate").start();
    }

    private void dlgViewAdd(LinearLayout col, android.graphics.Bitmap bm, android.app.Dialog vd, int[] gen, int my) {
        if (!vd.isShowing() || gen[0] != my) return;
        ImageView iv = new ImageView(this);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageBitmap(bm);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        col.addView(iv, lp);
    }

    /** Picture scaled down to about maxW wide, turned upright by its EXIF tag. */
    private android.graphics.Bitmap dlgDecode(Uri u, int maxW) throws java.io.IOException {
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (java.io.InputStream is = getContentResolver().openInputStream(u)) {
            android.graphics.BitmapFactory.decodeStream(is, null, o);
        }
        int sample = 1;
        while (o.outWidth / (sample * 2) >= maxW) sample *= 2;
        android.graphics.BitmapFactory.Options o2 = new android.graphics.BitmapFactory.Options();
        o2.inSampleSize = sample;
        android.graphics.Bitmap bmp;
        try (java.io.InputStream is = getContentResolver().openInputStream(u)) {
            bmp = android.graphics.BitmapFactory.decodeStream(is, null, o2);
        }
        int rot = 0;
        try (java.io.InputStream is = getContentResolver().openInputStream(u)) {
            int ori = new android.media.ExifInterface(is).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL);
            rot = ori == android.media.ExifInterface.ORIENTATION_ROTATE_90 ? 90
                    : ori == android.media.ExifInterface.ORIENTATION_ROTATE_180 ? 180
                    : ori == android.media.ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
        } catch (Exception ignored) { }
        if (bmp != null && rot != 0) {
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.postRotate(rot);
            bmp = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        }
        return bmp;
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf", "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "image/*"});
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, replaceIdx < 0);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
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
            // keep the caption the user typed, take the new file's extension and size
            String oldCap = DocFileCard.stem(pickedNames.get(replaceIdx));
            pickedNames.set(replaceIdx, oldCap + extOf(displayName(uri)));
            String[] m = pickedMeta.get(replaceIdx);
            m[3] = sizeText(fileSize(uri));
            m[5] = displayName(uri);
            m[4] = new SimpleDateFormat("dd MMM yyyy", Locale.US).format(new Date());
            replaceIdx = -1;
            return;
        }
        if (pickedUris.contains(uri)) return;
        pickedUris.add(uri);
        pickedMore.add(new ArrayList<>());
        pickedNames.add(displayName(uri));
        pickedMeta.add(new String[]{"", "", "", sizeText(fileSize(uri)),
                new SimpleDateFormat("dd MMM yyyy", Locale.US).format(new Date()), displayName(uri)});
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
        Collections.swap(pickedMeta, a, b);
        Collections.swap(pickedMore, a, b);
    }

    private List<Uri> groupOf(int idx) {
        List<Uri> g = new ArrayList<>();
        g.add(pickedUris.get(idx));
        g.addAll(pickedMore.get(idx));
        return g;
    }

    /** View / replace / delete / reorder / add more files of one document group. */
    private void manageFiles(int idx) {
        FileSetDialog.show(this, flow, "Files in this document", groupOf(idx), files -> applyGroup(idx, files));
    }

    private void applyGroup(int idx, List<Uri> files) {
        String oldPrimary = pickedUris.get(idx).toString();
        Uri first = files.get(0);
        pickedUris.set(idx, first);
        pickedMore.set(idx, new ArrayList<>(files.subList(1, files.size())));
        pickedNames.set(idx, DocFileCard.stem(pickedNames.get(idx)) + extOf(displayName(first)));
        String[] m = pickedMeta.get(idx);
        m[3] = FileSetDialog.totalSize(this, files);
        m[5] = FileSetDialog.label(this, files);
        LegalStore ls = new LegalStore(this);
        for (LegalStore.Doc d : ls.docs()) {
            if (oldPrimary.equals(d.uri)) {
                d.uri = first.toString();
                d.more.clear();
                for (int k = 1; k < files.size(); k++) d.more.add(files.get(k).toString());
                d.name = pickedNames.get(idx);
                d.size = m[3];
                d.fileName = m[5];
                ls.saveDoc(d);
                break;
            }
        }
        renderFiles();
    }

    /** Edit button on an uploaded file card: caption, category, matter, stage (also updates the Vault copy). */
    private void editPicked(int idx) {
        final String shown = pickedNames.get(idx);
        final String[] m = pickedMeta.get(idx);
        final String ext = extOf(shown);
        final String stem = ext.isEmpty() ? shown : shown.substring(0, shown.length() - ext.length());
        LinearLayout f = LegalUi.form(this);
        EditText name = LegalUi.text(this, f, "Document name / caption", stem);
        CategoryPicker cp = new CategoryPicker(this, f, m[0]);
        MatterPicker mp = new MatterPicker(this, f, m[1], m[2]);
        if (DocFileCard.kind(shown) == 0) {
            LinearLayout chip = LegalUi.chipButton(this, "Edit PDF pages (add / remove)",
                    IconPalette.pick(this, 0xFFE65100, 0xFFFFB74D), R.drawable.ic_edit);
            chip.setOnClickListener(v -> PdfPageEditor.open(this, flow, pickedUris.get(idx), neu -> {
                updateStoredDoc(pickedUris.get(idx), neu);
                pickedUris.set(idx, neu);
                m[3] = PdfTools.sizeLabel(this, neu);
                m[5] = PdfTools.fileName(this, neu);
                renderFiles();
            }));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.topMargin = dp(12);
            f.addView(chip, clp);
        }
        LinearLayout mchip = LegalUi.chipButton(this, "Manage files (add / replace / delete / reorder)",
                IconPalette.pick(this, 0xFF1565C0, 0xFF64B5F6), R.drawable.ic_image);
        mchip.setOnClickListener(v -> manageFiles(idx));
        LinearLayout.LayoutParams mlp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mlp2.topMargin = dp(12);
        f.addView(mchip, mlp2);
        LegalUi.formDialog(this, "Edit document", f, () -> {
            if (!LegalUi.need(this, name, "Document name")) return false;
            String neu = LegalUi.str(name) + ext;
            if (!mp.valid()) return false;
            String mt = mp.matter();
            String sg = mp.stage();
            pickedNames.set(idx, neu);
            m[0] = cp.get();
            m[1] = mt;
            m[2] = sg;
            String uri = pickedUris.get(idx).toString();
            LegalStore ls = new LegalStore(this);
            for (LegalStore.Doc d : ls.docs()) {
                if (uri.equals(d.uri)) {
                    d.name = neu;
                    d.category = m[0];
                    d.caseName = mt;
                    d.stage = sg;
                    ls.saveDoc(d);
                    break;
                }
            }
            renderFiles();
            return true;
        }, null);
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
            String[] m = i < pickedMeta.size() ? pickedMeta.get(i) : new String[]{"", "", "", "", "", ""};
            DocFileCard.Info info = new DocFileCard.Info();
            info.name = pickedNames.get(i);
            info.category = m[0];
            info.matter = m[1];
            info.stage = m[2];
            info.size = m[3];
            info.date = m[4];
            info.fileName = m.length > 5 ? m[5] : "";
            info.uri = pickedUris.get(i);
            DocFileCard.Actions act = new DocFileCard.Actions();
            final boolean grp = !pickedMore.get(i).isEmpty();
            info.count = 1 + pickedMore.get(i).size();
            act.open = grp ? () -> manageFiles(idx) : () -> openFile(idx);
            act.share = grp ? () -> DocFileCard.shareAll(this, groupOf(idx))
                    : () -> DocFileCard.share(this, pickedUris.get(idx), pickedNames.get(idx));
            act.files = () -> manageFiles(idx);
            if (grp) act.viewPdf = () -> {
                // same as the Document Vault: marked pages carry their category banner in the one PDF
                List<Uri> files = groupOf(idx);
                java.util.Map<String, java.util.Map<Integer, String>> pageMarks = new java.util.HashMap<>();
                MarkStore ms = new MarkStore(this);
                for (Uri fu : files) {
                    String fp = markerFiles.get(fu.toString());
                    if (fp != null) pageMarks.put(fu.toString(), ms.all(fp));
                }
                DocFileCard.viewAsOnePdf(this, files, DocFileCard.stem(pickedNames.get(idx)), pageMarks);
            };
            act.edit = () -> editPicked(idx);
            act.replace = () -> PickerDialogs.confirm(this, "Replace file?",
                    "\"" + pickedNames.get(idx) + "\" will be replaced by a new file.",
                    "Replace", () -> chooseSource(idx));
            act.delete = () -> PickerDialogs.confirm(this, "Delete file?",
                    "\"" + pickedNames.get(idx) + "\" will be removed from this case.",
                    "Delete", () -> {
                        pickedUris.remove(idx);
                        pickedMore.remove(idx);
                        pickedNames.remove(idx);
                        pickedMeta.remove(idx);
                        renderFiles();
                    });
            LinearLayout row = DocFileCard.build(this, info, act);

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
        tintUploadCard();
        if (scaled) shrink(filesBox);
    }

    /** The Upload Legal Document card turns green once a document is saved in it, plain again when emptied. */
    private void tintUploadCard() {
        if (uploadCardView == null) return;
        if (pickedUris.isEmpty()) {
            uploadCardView.setBackground(CaseViews.box(this, AppTheme.card(this), 18, AppTheme.cardStroke(this), 1));
        } else {
            uploadCardView.setBackground(CaseViews.box(this, AppTheme.isDark(this) ? 0xFF1B3A24 : 0xFFE3F4E6,
                    18, AppTheme.topics(this), 2));
        }
    }

    /** "Multiple" when the marked pages use several categories, the one category when only one, else "Other". */
    private static String markedCategory(java.util.Map<Integer, String> marks) {
        java.util.Set<String> d = new java.util.LinkedHashSet<>(marks.values());
        if (d.isEmpty()) return "Other";
        return d.size() == 1 ? d.iterator().next() : MULTIPLE;
    }

    // ---------- parties: field + chip look ----------

    private TextView partyLabel(String s) {
        return CaseViews.fieldLabel(this, s);
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
        card.setPadding(dp(12), dp(8), dp(12), dp(8));
        clientSideCard = card;
        clientSideInvalid(false);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(CaseViews.text(this, "My client is *", 15, AppTheme.text(this), false),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final TextView req = CaseViews.text(this, "Required", 12, AppTheme.sub(this), false);
        req.setPadding(dp(10), dp(4), dp(2), dp(4));
        head.addView(req);
        card.addView(head);

        final LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        card.addView(grid, lpTop(6));
        final Runnable[] draw = new Runnable[1];
        draw[0] = () -> {
            grid.removeAllViews();
            if (clientRole.isEmpty()) {              // nothing chosen: plain "Required" tag
                req.setText(vaultSaved ? "Optional" : "Required");
                req.setTextColor(AppTheme.sub(this));
                req.setOnClickListener(null);
                req.setClickable(false);
            } else {                                 // chosen: visible Clear button to undo a mistaken pick
                req.setText("\u2715  Clear");
                req.setTextColor(RoundEdit.RED);
                req.setOnClickListener(v -> {
                    clientRole = "";
                    draw[0].run();
                    refresh();
                });
            }
            final int perRow = 3;
            for (int start = 0; start < CLIENT_SIDES.length; start += perRow) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                for (int k = 0; k < perRow; k++) {
                    int i = start + k;
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                    if (k < perRow - 1) lp.rightMargin = dp(6);
                    if (i >= CLIENT_SIDES.length) {          // keep the last row's chips the same width
                        row.addView(new View(this), lp);
                        continue;
                    }
                    final String side = CLIENT_SIDES[i];
                    boolean on = side.equals(clientRole);
                    LinearLayout chip = new LinearLayout(this);
                    chip.setOrientation(LinearLayout.VERTICAL);
                    chip.setGravity(Gravity.CENTER);
                    chip.setPadding(dp(4), dp(5), dp(4), dp(5));
                    chip.setBackground(CaseViews.box(this, AppTheme.field(this), 14,
                            on ? AppTheme.text(this) : AppTheme.fieldStroke(this), on ? 2 : 1));
                    int fg = on ? AppTheme.text(this) : AppTheme.sub(this);
                    chip.addView(CaseViews.icon(this, on ? R.drawable.ic_check_circle : CLIENT_ICONS[i], fg, 16));
                    TextView t = CaseViews.text(this, side, 12, fg, on);
                    t.setGravity(Gravity.CENTER);
                    t.setSingleLine(false);
                    t.setMaxLines(2);
                    t.setPadding(0, dp(2), 0, 0);
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
                if (start > 0) rl.topMargin = dp(6);
                grid.addView(row, rl);
            }
            if (scaled) shrink(grid);
        };
        draw[0].run();
        return card;
    }

    // ---------- opposing advocate: own card with name, saved list / contacts, visiting card photo ----------

    private static final int REQ_ADV_CONTACT = 55, REQ_ADV_PERM = 56, REQ_ADV_IMG = 57, REQ_REF_CONTACT = 58, REQ_REF_PERM = 59;

    private static final class Adv {
        EditText name;
        String mobile = "", card = "";
        ImageView thumb;
        TextView hint;
    }

    private final List<Adv> advs = new ArrayList<>();
    private LinearLayout advBox;
    private Adv advTarget;

    private LinearLayout buildAdvocateForm() {
        LinearLayout f = subForm();
        advBox = new LinearLayout(this);
        advBox.setOrientation(LinearLayout.VERTICAL);
        int col = IconPalette.pick(this, 0xFF6A1B9A, 0xFFCE93D8);
        f.addView(labelWithAdd("Opposing Advocate", "Add", col, this::addAdvocate));
        f.addView(advBox, lpTop(8));
        addAdvocate();
        return f;
    }

    private void addAdvocate() {
        final boolean first = advs.isEmpty();
        final Adv a = new Adv();
        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(CaseViews.box(this, AppTheme.card(this), 16, AppTheme.cardStroke(this), 1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        a.name = new RoundEdit(this);
        a.name.setInputType(NAME);
        FieldStyle.hint(a.name, "Enter Opposing Advocate Name");
        a.name.setSingleLine(true);
        advRows.add(a.name);
        ImageView x = CaseViews.icon(this, R.drawable.ic_close, AppTheme.sub(this), 20);
        x.setPadding(dp(10), dp(10), dp(10), dp(10));
        x.setContentDescription("Remove advocate");
        if (first) {
            x.setVisibility(View.INVISIBLE);
        } else {
            x.setOnClickListener(v -> {
                advBox.removeView(card);
                advs.remove(a);
                advRows.remove(a.name);
            });
        }
        top.addView(a.name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(x, new LinearLayout.LayoutParams(dp(40), dp(40)));
        card.addView(top);

        int col = IconPalette.pick(this, 0xFF6A1B9A, 0xFFCE93D8);
        LinearLayout pick = new LinearLayout(this);
        pick.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout saved = LegalUi.chipButton(this, "Saved list", col, R.drawable.ic_person);
        saved.setOnClickListener(v -> pickSavedAdvocate(a));
        LinearLayout cont = LegalUi.chipButton(this, "Phone contacts", col, R.drawable.ic_contacts);
        cont.setOnClickListener(v -> pickAdvocateContact(a));
        pick.addView(saved);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(8);
        pick.addView(cont, clp);
        card.addView(pick, lpTop(8));

        LinearLayout slot = new LinearLayout(this);
        slot.setOrientation(LinearLayout.HORIZONTAL);
        slot.setGravity(Gravity.CENTER_VERTICAL);
        slot.setPadding(dp(10), dp(10), dp(10), dp(10));
        slot.setBackground(CaseViews.box(this, AppTheme.field(this), 14, AppTheme.fieldStroke(this), 1));
        a.thumb = new ImageView(this);
        a.thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        slot.addView(a.thumb, new LinearLayout.LayoutParams(dp(56), dp(56)));
        a.hint = CaseViews.text(this, "", 13, AppTheme.sub(this), false);
        a.hint.setPadding(dp(12), 0, 0, 0);
        slot.addView(a.hint, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        slot.setOnClickListener(v -> cardActions(a));
        card.addView(slot, lpTop(10));

        advs.add(a);
        advBox.addView(card, lpTop(first ? 0 : 10));
        refreshCard(a);
        if (scaled) shrink(card);
    }

    private void setAdvocate(Adv a, String name, String mobile, String card) {
        if (name != null && !name.isEmpty()) a.name.setText(name);
        a.mobile = mobile == null ? "" : mobile;
        if (card != null && !card.isEmpty() && new File(card).exists()) a.card = card;
        refreshCard(a);
    }

    private void refreshCard(Adv a) {
        android.graphics.Bitmap b = a.card.isEmpty() ? null : decodeCard(a.card, 200);
        if (b != null) {
            a.thumb.clearColorFilter();
            a.thumb.setPadding(0, 0, 0, 0);
            a.thumb.setImageBitmap(b);
            a.hint.setText("Visiting card added. Tap to view or change.");
        } else {
            a.card = "";
            a.thumb.setPadding(dp(14), dp(14), dp(14), dp(14));
            a.thumb.setImageResource(R.drawable.ic_camera);
            a.thumb.setColorFilter(AppTheme.sub(this));
            a.hint.setText("Visiting card photo. Tap to take or choose.");
        }
    }

    private static android.graphics.Bitmap decodeCard(String path, int max) {
        try {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(path, o);
            int s = 1;
            while (o.outWidth / (s * 2) >= max && o.outHeight / (s * 2) >= max) s *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = s;
            return android.graphics.BitmapFactory.decodeFile(path, o);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private void cardActions(final Adv a) {
        advTarget = a;
        if (a.card.isEmpty()) {
            PickerDialogs.actions(this, "Visiting card",
                    new String[]{"Take photo", "Choose from gallery"},
                    new int[]{R.drawable.ic_camera, R.drawable.ic_image},
                    i -> { if (i == 0) shootCard(); else pickCardImage(); });
        } else {
            PickerDialogs.actions(this, "Visiting card",
                    new String[]{"View", "Retake photo", "Choose from gallery", "Remove"},
                    new int[]{R.drawable.ic_eye, R.drawable.ic_camera, R.drawable.ic_image, R.drawable.ic_delete},
                    i -> {
                        if (i == 0) {
                            Intent v = new Intent(Intent.ACTION_VIEW);
                            v.setDataAndType(PhotoFlow.uri(this, new File(a.card)), "image/*");
                            v.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            try { startActivity(v); } catch (Exception e) {
                                Toast.makeText(this, "No app found to open this image", Toast.LENGTH_SHORT).show();
                            }
                        } else if (i == 1) shootCard();
                        else if (i == 2) pickCardImage();
                        else { a.card = ""; refreshCard(a); }
                    });
        }
    }

    private void shootCard() {
        flow.start(false, new PhotoFlow.Result() {
            @Override public void images(List<File> files) {
                if (files.isEmpty() || advTarget == null) return;
                try {
                    storeCard(advTarget, new java.io.FileInputStream(files.get(0)));
                } catch (Exception e) {
                    Toast.makeText(NewCaseActivity.this, "Could not save the card photo", Toast.LENGTH_SHORT).show();
                }
                for (File f : files) f.delete();
            }
            @Override public void pdf(File file) {}
        });
    }

    private void pickCardImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(i, REQ_ADV_IMG);
        } catch (Exception e) {
            Toast.makeText(this, "No gallery available on this device", Toast.LENGTH_LONG).show();
        }
    }

    /** Copy the picture into the app's own folder so it stays after the camera / gallery file goes. */
    private void storeCard(Adv a, java.io.InputStream in) throws java.io.IOException {
        File dir = DataFolder.dir("Cards", new File(getFilesDir(), "adv_cards"));
        dir.mkdirs();
        File out = new File(dir, "card_" + System.currentTimeMillis() + ".jpg");
        try (java.io.InputStream src = in; java.io.OutputStream dst = new java.io.FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = src.read(buf)) > 0) dst.write(buf, 0, n);
        }
        a.card = out.getAbsolutePath();
        refreshCard(a);
    }

    private void pickSavedAdvocate(final Adv a) {
        final List<AdvocateStore.Adv> all = new AdvocateStore(this).list();
        if (all.isEmpty()) {
            Toast.makeText(this, "No saved advocate yet. They are saved when you create a case.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String[] items = new String[all.size()];
        for (int i = 0; i < items.length; i++) {
            AdvocateStore.Adv v = all.get(i);
            items[i] = v.mobile.isEmpty() ? v.name : v.name + "  \u2022  " + v.mobile;
        }
        new AlertDialog.Builder(this)
                .setTitle("Saved advocates")
                .setItems(items, (d, w) -> {
                    AdvocateStore.Adv v = all.get(w);
                    setAdvocate(a, v.name, v.mobile, v.card);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void pickAdvocateContact(Adv a) {
        advTarget = a;
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startActivityForResult(new Intent(this, ChooseContactActivity.class), REQ_ADV_CONTACT);
        } else {
            requestPermissions(new String[]{android.Manifest.permission.READ_CONTACTS}, REQ_ADV_PERM);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_REF_PERM) {
            if (results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startActivityForResult(new Intent(this, ChooseContactActivity.class), REQ_REF_CONTACT);
            } else {
                Toast.makeText(this, "Allow contacts permission to pick from phone contacts", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (code != REQ_ADV_PERM) return;
        if (results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startActivityForResult(new Intent(this, ChooseContactActivity.class), REQ_ADV_CONTACT);
        } else {
            Toast.makeText(this, "Allow contacts permission to pick from phone contacts", Toast.LENGTH_LONG).show();
        }
    }

    /** Remember each advocate (name, number, card photo) so next case can pick them from the saved list. */
    private void saveAdvocates() {
        AdvocateStore st = new AdvocateStore(this);
        for (Adv a : advs) st.save(LegalUi.str(a.name), a.mobile, a.card);
    }

    // ---------- several names: petitioners, respondents, opposing advocates ----------

    private void multiNames(LinearLayout f, String label, String addLabel, String hint, List<EditText> rows,
                            int lightColor, int darkColor) {
        int col = IconPalette.pick(this, lightColor, darkColor);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        f.addView(labelWithAdd(label, addLabel, col, () -> addNameRow(box, rows, hint)));
        f.addView(box, lpTop(8));
        addNameRow(box, rows, hint);
    }

    /** Field label on the left, small "Add" chip on the same line at the right. */
    private LinearLayout labelWithAdd(String label, String addLabel, int color, Runnable onAdd) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(partyLabel(label), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout add = LegalUi.chipButton(this, addLabel, color);
        add.setOnClickListener(v -> onAdd.run());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        alp.leftMargin = dp(8);
        head.addView(add, alp);
        return head;
    }

    private void addNameRow(LinearLayout box, List<EditText> rows, String hint) {
        final boolean first = box.getChildCount() == 0;
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final EditText e = new RoundEdit(this);
        e.setInputType(NAME);
        FieldStyle.hint(e, hint);
        e.setSingleLine(true);
        ImageView x = CaseViews.icon(this, R.drawable.ic_close, AppTheme.sub(this), 20);
        x.setPadding(dp(10), dp(10), dp(10), dp(10));
        x.setContentDescription("Remove");
        rows.add(e);
        x.setOnClickListener(v -> {               // any row can go, first included; the next one moves up
            if (rows.size() < 2) return;
            box.removeView(row);
            rows.remove(e);
            crossesFor(box);
        });
        row.addView(e, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(x, new LinearLayout.LayoutParams(dp(40), dp(40)));
        box.addView(row, lpTop(first ? 0 : 8));
        if (scaled) shrink(row);
        crossesFor(box);
    }

    /** Cross on every row as soon as there are two or more; a single row has none. */
    private void crossesFor(LinearLayout box) {
        int n = box.getChildCount();
        for (int i = 0; i < n; i++) {
            LinearLayout row = (LinearLayout) box.getChildAt(i);
            row.getChildAt(1).setVisibility(n > 1 ? View.VISIBLE : View.INVISIBLE);
            ((LinearLayout.LayoutParams) row.getLayoutParams()).topMargin = i == 0 ? 0 : dp(8);
            row.requestLayout();
        }
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

    /** Number box (takes the free width) and a narrow 4-digit year box side by side, labels above each. */
    private EditText[] numberYear(LinearLayout parent, String numLabel, String yearLabel) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        EditText num = LegalUi.text(this, left, numLabel, "");
        EditText year = LegalUi.input(this, right, yearLabel, "", InputType.TYPE_CLASS_NUMBER);
        FieldStyle.hint(year, "2024");
        year.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
        row.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(dp(104), LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.leftMargin = dp(10);
        row.addView(right, rlp);
        parent.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return new EditText[]{num, year};
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
        FieldStyle.hint(a, "Act (e.g. IPC)");
        a.setSingleLine(true);
        EditText s = new RoundEdit(this);
        FieldStyle.hint(s, "Sections");
        s.setSingleLine(true);
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
        FieldStyle.hint(s, "Section (e.g. 420 IPC)");
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
        createBtn = create;
        b.addView(create, lpTop(10));
        return b;
    }

    /** Vault-only: no field is mandatory any more, so drop every "*" and "Required" mark on the page. */
    private void relaxRequiredLabels(View v) {
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) relaxRequiredLabels(g.getChildAt(i));
        } else if (v instanceof TextView && !(v instanceof EditText)) {
            TextView t = (TextView) v;
            String x = t.getText().toString().trim();
            if (x.equals("Required")) t.setText("Optional");
            else if (x.startsWith("Fields marked *")) t.setText("All fields are optional now.");
            else if (x.endsWith("*") && x.length() <= 40) t.setText(x.substring(0, x.length() - 1).trim());
        }
    }

    /** Case No. lives inside the collapsed identifiers section: expand both levels so the red mark shows. */
    private void openIdentifiers() {
        if (extraBox != null && extraBox.getVisibility() != View.VISIBLE) extraHead.performClick();
        if (idForm != null && idForm.getVisibility() != View.VISIBLE) idSection.getChildAt(1).performClick();
    }

    private List<String> missing() {
        List<String> m = new ArrayList<>();
        if (vaultSaved) {                       // vault-only: clear every red "required" mark, require nothing
            ((RoundEdit) titleEdit).setInvalid(false);
            ((RoundEdit) regEdit).setInvalid(false);
            ((RoundEdit) caseNoEdit).setInvalid(false);
            dropInvalid(courtNameText, false);
            dropInvalid(caseTypeText, false);
            clientSideInvalid(false);
            return m;
        }
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
        line(sb, "Referred by", referredBy);
        line(sb, "Case title", LegalUi.str(titleEdit));
        line(sb, "Registration date", LegalUi.str(regEdit));
        line(sb, "Court name / type", courtName);
        line(sb, "Case type", caseType);
        line(sb, "Case no.", LegalUi.str(caseNoEdit));
        line(sb, "CNR", cnrValue());
        line(sb, "Filing\\Diary no.", LegalUi.str(filingEdit));
        line(sb, "Filing\\Diary year", LegalUi.str(filingYearEdit));
        line(sb, "Case year", LegalUi.str(caseYearEdit));
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
            if (m.contains("Case No.")) openIdentifiers();
            Toast.makeText(this, "Required: " + android.text.TextUtils.join(", ", m),
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (vaultSaved && doneCount() == 0) {   // only a reference document: it is already in the Vault
            Toast.makeText(this, "Document saved in Document Vault", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        LegalStore.Client cl = clientId.isEmpty() ? null : legal.client(clientId);

        DiaryStore.HCase c = new DiaryStore.HCase();
        c.caseTitle = LegalUi.str(titleEdit).isEmpty() ? "Untitled case" : LegalUi.str(titleEdit);
        c.regDate = LegalUi.str(regEdit);
        c.courtKind = courtKind;
        c.court = courtName;
        c.type = caseType;
        c.number = LegalUi.str(caseNoEdit);
        c.clientId = clientId;
        c.referredBy = referredBy;

        String pet = names(petRows);
        c.first = pet.isEmpty() ? c.caseTitle : pet;
        c.second = names(resRows);
        c.appearingFor = clientRole;
        c.opposingAdvocate = names(advRows);
        saveAdvocates();
        c.representing = cl != null ? cl.name : clientRole;

        c.cnr = cnrValue();
        c.filingNo = LegalUi.str(filingEdit);
        c.diaryNo = c.filingNo;                 // one shared Filing\\Diary number and year
        c.filingYear = LegalUi.str(filingYearEdit);
        c.diaryYear = c.filingYear;
        c.caseYear = LegalUi.str(caseYearEdit);
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
        c.subType = subTypeVal;
        c.notes = LegalUi.str(notesEdit);
        c.firStation = LegalUi.str(stationEdit);
        c.firNo = LegalUi.str(firNoEdit);
        c.firYear = LegalUi.str(firYearEdit);
        c.firSections = firSectionsText();
        c.firDate = LegalUi.str(firDateEdit);
        c.acts = actsText();

        store.saveCase(c);
        if (!pendingNewCaseDocs.isEmpty()) {
            LegalStore docStore = new LegalStore(this);
            for (LegalStore.Doc pd : docStore.docs()) {
                if (pendingNewCaseDocs.contains(pd.uri)) {
                    pd.caseName = c.title();
                    docStore.saveDoc(pd);
                }
            }
        }
        Toast.makeText(this, "Case created", Toast.LENGTH_SHORT).show();
        finish();
    }
}
