package com.example.twobuttons;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Add New Client page. Save turns on when a name is filled in. Phone is optional, but if typed it must be valid
 * for the chosen country code. Fields match the New Case page look.
 */
public class AddClientActivity extends BaseActivity {

    private static final int REQ_PERM = 31, REQ_CONTACT = 32;

    private EditText nameEdit, emailEdit, mobileEdit, waEdit, landEdit, addrEdit, notesEdit;
    private TextView mobileCode, waCode, landCode, mobileErr, waErr, landErr, emailErr, saveBtn;
    private String mobileDial = CountryCodes.DEFAULT_DIAL;
    // what "Choose from Contacts" filled in; if the user edits either, the client counts as custom
    private String pickedName = null, pickedMobile = null;
    private String landDial = CountryCodes.DEFAULT_DIAL, waDial = CountryCodes.DEFAULT_DIAL;
    private boolean canSave = false;
    private LegalStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().hide();
        store = new LegalStore(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(AppTheme.bg(this));
        root.addView(header(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView sv = new ScrollView(this);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        body.setPadding(pad, pad, pad, pad * 2);
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // choose from contacts
        LinearLayout pick = new LinearLayout(this);
        pick.setOrientation(LinearLayout.HORIZONTAL);
        pick.setGravity(Gravity.CENTER);
        pick.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable dash = new GradientDrawable();
        dash.setColor(0x00000000);
        dash.setCornerRadius(dp(28));
        dash.setStroke(dp(2), AppTheme.accent(this), dp(8), dp(6));
        pick.setBackground(dash);
        pick.addView(CaseViews.icon(this, R.drawable.ic_contacts, AppTheme.accent(this), 22));
        TextView pt = CaseViews.text(this, "Choose from Contacts", 15, AppTheme.accent(this), false);
        pt.setPadding(dp(10), 0, 0, 0);
        pick.addView(pt);
        pick.setOnClickListener(v -> chooseFromContacts());
        body.addView(pick, CaseViews.lp(this, 0, 16));

        // basic information card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(20));
        card.setBackground(CaseViews.box(this, AppTheme.card(this), 18, AppTheme.cardStroke(this), 1));
        body.addView(card);

        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.HORIZONTAL);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(CaseViews.icon(this, R.drawable.ic_person, AppTheme.accent(this), 22));
        TextView tt = CaseViews.text(this, "Basic Information", 17, AppTheme.text(this), true);
        tt.setPadding(dp(10), 0, 0, 0);
        title.addView(tt);
        card.addView(title);

        // full name
        card.addView(label(R.drawable.ic_person, "Full Name *"));
        nameEdit = edit("Enter client's full name",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, 0);
        ((RoundEdit) nameEdit).setRequired(true);
        card.addView(wrap(nameEdit, null), CaseViews.lp(this, 0, 0));

        // email
        card.addView(label(R.drawable.ic_mail, "Email (Optional)"));
        emailEdit = edit("Enter email address",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, 0);
        card.addView(wrap(emailEdit, null), CaseViews.lp(this, 0, 0));
        emailErr = errorText();
        card.addView(emailErr);

        // mobile (country code mandatory)
        card.addView(label(R.drawable.ic_phone, "Phone Number (Optional)"));
        mobileCode = codeChip(0);
        mobileEdit = edit("Enter phone number", InputType.TYPE_CLASS_NUMBER, 0);
        mobileEdit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(15)});
        card.addView(wrap(mobileEdit, mobileCode), CaseViews.lp(this, 0, 0));
        mobileErr = errorText();
        card.addView(mobileErr);

        // whatsapp (optional, same validity rule as the phone number)
        card.addView(label(R.drawable.ic_phone, "WhatsApp Number (Optional)"));
        waCode = codeChip(2);
        waEdit = edit("Enter WhatsApp number", InputType.TYPE_CLASS_NUMBER, 0);
        waEdit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(15)});
        card.addView(wrap(waEdit, waCode), CaseViews.lp(this, 0, 0));
        waErr = errorText();
        card.addView(waErr);

        // fixed phone / landline
        card.addView(label(R.drawable.ic_call, "Landline / Fixed Phone (Optional)"));
        landCode = codeChip(1);
        landEdit = edit("Area code + number", InputType.TYPE_CLASS_NUMBER, 0);
        landEdit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(15)});
        card.addView(wrap(landEdit, landCode), CaseViews.lp(this, 0, 0));
        landErr = errorText();
        card.addView(landErr);

        // address
        card.addView(label(R.drawable.ic_location, "Address"));
        addrEdit = edit("Enter address", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 3);
        card.addView(wrap(addrEdit, null), CaseViews.lp(this, 0, 0));

        // notes
        card.addView(label(R.drawable.ic_notes, "Notes"));
        notesEdit = edit("Add any additional notes", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 3);
        card.addView(wrap(notesEdit, null), CaseViews.lp(this, 0, 0));

        TextWatcher w = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { validate(); }
        };
        nameEdit.addTextChangedListener(w);
        emailEdit.addTextChangedListener(w);
        mobileEdit.addTextChangedListener(w);
        waEdit.addTextChangedListener(w);
        landEdit.addTextChangedListener(w);

        setContentView(root);
        shrink(root);
        validate();
    }

    /** Same 0.9 text scale as the New Case page; RoundEdit and "keep"-tagged views stay 15sp. */
    private void shrink(View v) {
        if (v instanceof TextView && !(v instanceof RoundEdit) && !"keep".equals(v.getTag())) {
            TextView t = (TextView) v;
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, t.getTextSize() * 0.9f);
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) shrink(g.getChildAt(i));
        }
    }

    // ---------- views ----------

    private int dp(int v) { return CaseViews.dp(this, v); }

    private LinearLayout header() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(8), dp(10), dp(12), dp(10));

        android.widget.ImageView back = CaseViews.icon(this, R.drawable.ic_arrow_back, AppTheme.text(this), 24);
        back.setPadding(dp(8), dp(8), dp(8), dp(8));
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        h.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView t = CaseViews.text(this, "Add New Client", 18, AppTheme.text(this), true);
        t.setGravity(Gravity.CENTER);
        h.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        saveBtn = CaseViews.text(this, "Save", 14, 0xFFFFFFFF, true);
        saveBtn.setGravity(Gravity.CENTER);
        saveBtn.setPadding(dp(22), dp(10), dp(22), dp(10));
        saveBtn.setBackground(CaseViews.box(this, 0xFF2F6FE4, 12, 0, 0));
        saveBtn.setOnClickListener(v -> save());
        h.addView(saveBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return h;
    }

    private LinearLayout label(int icon, String text) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(10), 0, dp(4));
        r.addView(CaseViews.icon(this, icon, AppTheme.accent(this), 16));
        TextView t = CaseViews.fieldLabel(this, text);
        t.setPadding(dp(8), 0, 0, 0);
        r.addView(t);
        return r;
    }

    private EditText edit(String hint, int inputType, int minLines) {
        EditText e = new RoundEdit(this);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setTextSize(15);
        e.setTextColor(AppTheme.text(this));
        e.setHintTextColor(AppTheme.sub(this));
        e.setBackground(null);
        if (minLines > 0) {
            e.setMinLines(minLines);
            e.setGravity(Gravity.TOP);
            e.setPadding(0, dp(12), 0, dp(12));
        } else {
            e.setSingleLine(true);
            e.setPadding(0, dp(12), 0, dp(12));
        }
        return e;
    }

    private GradientDrawable fieldBg(boolean focus) {
        return CaseViews.box(this, AppTheme.field(this), 12,
                focus ? AppTheme.accent(this) : AppTheme.fieldStroke(this), focus ? 2 : 1);
    }

    /** Rounded field box around an EditText, with an optional leading view (country code). */
    private LinearLayout wrap(EditText e, View leading) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(16), 0, dp(16), 0);
        c.setBackground(fieldBg(false));
        if (leading != null) {
            c.addView(leading, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            View div = new View(this);
            div.setBackgroundColor(AppTheme.fieldStroke(this));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(1), dp(26));
            dl.leftMargin = dp(10);
            dl.rightMargin = dp(12);
            c.addView(div, dl);
        }
        c.addView(e, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return c;
    }

    /** kind: 0 mobile, 1 landline, 2 WhatsApp. */
    private TextView codeChip(int kind) {
        TextView t = CaseViews.text(this, CountryCodes.label(CountryCodes.DEFAULT_DIAL) + " \u25BE",
                15, AppTheme.text(this), true);
        t.setPadding(0, dp(12), 0, dp(12));
        t.setTag("keep");
        t.setOnClickListener(v -> CountryCodes.pick(this, kind == 0 ? mobileDial : kind == 1 ? landDial : waDial, dial -> {
            if (kind == 0) mobileDial = dial; else if (kind == 1) landDial = dial; else waDial = dial;
            t.setText(CountryCodes.label(dial) + " \u25BE");
            validate();
        }));
        return t;
    }

    private TextView errorText() {
        TextView t = CaseViews.text(this, "", 13, LegalUi.RED, false);
        t.setPadding(dp(4), dp(6), 0, 0);
        t.setVisibility(View.GONE);
        return t;
    }

    private static void show(TextView t, String msg) {
        if (msg == null) {
            t.setVisibility(View.GONE);
        } else {
            t.setText(msg);
            t.setVisibility(View.VISIBLE);
        }
    }

    // ---------- validation + save ----------

    private void validate() {
        String name = nameEdit.getText().toString().trim();
        String mob = mobileEdit.getText().toString().trim();
        String land = landEdit.getText().toString().trim();
        String wa = waEdit.getText().toString().trim();
        String email = emailEdit.getText().toString().trim();

        String me = mob.isEmpty() ? null : CountryCodes.mobileError(mobileDial, mob);
        String le = land.isEmpty() ? null : CountryCodes.landlineError(land);
        String we = wa.isEmpty() ? null : CountryCodes.mobileError(waDial, wa);
        String ee = (email.isEmpty() || Patterns.EMAIL_ADDRESS.matcher(email).matches())
                ? null : "Enter a valid email address";
        show(mobileErr, me);
        show(waErr, we);
        show(landErr, le);
        show(emailErr, ee);
        ((RoundEdit) mobileEdit).setInvalid(me != null);
        ((RoundEdit) waEdit).setInvalid(we != null);
        ((RoundEdit) landEdit).setInvalid(le != null);
        ((RoundEdit) emailEdit).setInvalid(ee != null);

        canSave = !name.isEmpty() && me == null && we == null && le == null && ee == null;
        saveBtn.setEnabled(canSave);
        saveBtn.setAlpha(canSave ? 1f : 0.4f);
    }

    private void save() {
        if (!canSave) return;
        String land = landEdit.getText().toString().trim();
        LegalStore.Client c = new LegalStore.Client();
        c.code = store.nextClientCode();
        c.name = nameEdit.getText().toString().trim();
        String mob = mobileEdit.getText().toString().trim();
        c.mobile = mob.isEmpty() ? "" : mobileDial + mob;
        c.fromContacts = pickedName != null && c.name.equals(pickedName) && c.mobile.equals(pickedMobile);
        String wa = waEdit.getText().toString().trim();
        c.whatsapp = wa.isEmpty() ? "" : waDial + wa;
        c.landline = land.isEmpty() ? "" : landDial + land;
        c.email = emailEdit.getText().toString().trim();
        c.address = addrEdit.getText().toString().trim();
        c.notes = notesEdit.getText().toString().trim();
        store.saveClient(c);
        setResult(RESULT_OK, new Intent().putExtra("client_id", c.id));
        Toast.makeText(this, "Client saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    // ---------- contacts ----------

    private void chooseFromContacts() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            openPicker();
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_PERM);
        }
    }

    private void openPicker() {
        startActivityForResult(new Intent(this, ChooseContactActivity.class), REQ_CONTACT);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != REQ_PERM) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            openPicker();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage("Please grant contacts permission to use this feature.")
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Settings", (d, w) -> startActivity(new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()))))
                    .show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_CONTACT || res != RESULT_OK || data == null) return;
        String name = data.getStringExtra("name");
        String number = data.getStringExtra("number");
        String[] p = CountryCodes.parse(number, CountryCodes.DEFAULT_DIAL);
        nameEdit.setText(name == null ? "" : name);
        mobileDial = p[0];
        mobileCode.setText(CountryCodes.label(mobileDial) + " \u25BE");
        mobileEdit.setText(p[1]);
        // read back from the fields, so input filters can't make the saved value differ from what was picked
        pickedName = nameEdit.getText().toString().trim();
        pickedMobile = mobileDial + mobileEdit.getText().toString().trim();
        validate();
        Toast.makeText(this, "Contact Selected\n" + (name == null ? "" : name)
                + "'s details have been filled", Toast.LENGTH_SHORT).show();
    }
}
