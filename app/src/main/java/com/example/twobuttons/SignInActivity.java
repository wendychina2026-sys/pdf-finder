package com.example.twobuttons;

import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Sarth sign-in screen. Layout only: login is not connected to any server. */
public class SignInActivity extends BaseActivity {

    private static final int PURPLE = 0xFF9C7FD6;

    private EditText id, pass;
    private CheckBox agree;
    private boolean shown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Sign In");
        int p = LegalUi.dp(this, 24);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(AppTheme.bg(this));
        sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p, p, p, p);

        // logo
        LinearLayout logo = new LinearLayout(this);
        logo.setOrientation(LinearLayout.VERTICAL);
        logo.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView brand = CaseViews.text(this, "SARTH", 22, PURPLE, true);
        brand.setLetterSpacing(0.2f);
        logo.addView(brand);
        TextView shield = CaseViews.text(this, "S", 52, 0xFFFFFFFF, true);
        shield.setGravity(Gravity.CENTER);
        shield.setBackground(CaseViews.box(this, PURPLE, 18, 0xFF7E57C2, 2));
        logo.addView(shield, new LinearLayout.LayoutParams(LegalUi.dp(this, 100), LegalUi.dp(this, 110)));
        root.addView(logo, CaseViews.lp(this, 24, 16));

        root.addView(CaseViews.text(this, "Sign In", 30, AppTheme.text(this), false), CaseViews.lp(this, 0, 14));

        LinearLayout idRow = field(R.drawable.ic_phone, "Mobile No/ Email ID", false);
        id = (EditText) idRow.getChildAt(1);
        root.addView(idRow, CaseViews.lp(this, 0, 12));

        LinearLayout pwRow = field(R.drawable.ic_lock, "Password", true);
        pass = (EditText) pwRow.getChildAt(1);
        ImageView eye = CaseViews.icon(this, R.drawable.ic_eye, AppTheme.text(this), 26);
        eye.setOnClickListener(v -> {
            shown = !shown;
            pass.setTransformationMethod(shown ? null : PasswordTransformationMethod.getInstance());
            pass.setSelection(pass.getText().length());
        });
        pwRow.addView(eye);
        root.addView(pwRow, CaseViews.lp(this, 0, 8));

        TextView forgot = CaseViews.text(this, "Forgot Password?", 15, AppTheme.text(this), true);
        forgot.setGravity(Gravity.END);
        forgot.setOnClickListener(v -> toast("Forgot password: not connected"));
        root.addView(forgot, CaseViews.lp(this, 0, 14));

        LinearLayout terms = new LinearLayout(this);
        terms.setOrientation(LinearLayout.HORIZONTAL);
        terms.setGravity(Gravity.CENTER_VERTICAL);
        agree = new CheckBox(this);
        terms.addView(agree);
        terms.addView(CaseViews.text(this, "I agree to the Terms of Use & Privacy Policy", 14, AppTheme.sub(this), false));
        root.addView(terms, CaseViews.lp(this, 0, 18));

        Button login = new Button(this);
        login.setText("Login");
        login.setAllCaps(false);
        login.setTextColor(0xFFFFFFFF);
        login.setTextSize(17);
        login.setBackground(CaseViews.box(this, 0xFF58595B, 14, 0, 0));
        login.setOnClickListener(v -> {
            if (!agree.isChecked()) { toast("Accept Terms of Use & Privacy Policy first"); return; }
            if (id.getText().toString().trim().isEmpty() || pass.getText().length() == 0) {
                toast("Enter mobile / email and password");
                return;
            }
            toast("Layout only. Login not connected.");
        });
        root.addView(login, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LegalUi.dp(this, 54)));

        TextView or = CaseViews.text(this, "\u2500\u2500\u2500\u2500\u2500  Or Login with  \u2500\u2500\u2500\u2500\u2500",
                14, AppTheme.sub(this), false);
        or.setGravity(Gravity.CENTER);
        root.addView(or, CaseViews.lp(this, 22, 22));

        Button g = new Button(this);
        g.setText("G   Sign in with Google");
        g.setAllCaps(false);
        g.setTextColor(0xFFFFFFFF);
        g.setTextSize(17);
        g.setBackground(CaseViews.box(this, 0xFF4285F4, 10, 0, 0));
        g.setOnClickListener(v -> toast("Google sign-in: not connected"));
        root.addView(g, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LegalUi.dp(this, 54)));

        Button create = new Button(this);
        create.setText("Create an Account");
        create.setAllCaps(false);
        create.setTextColor(AppTheme.text(this));
        create.setTextSize(17);
        create.setBackground(CaseViews.box(this, AppTheme.card(this), 10, AppTheme.cardStroke(this), 1));
        create.setOnClickListener(v -> toast("Create account: not connected"));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LegalUi.dp(this, 54));
        clp.topMargin = LegalUi.dp(this, 48);
        root.addView(create, clp);

        TextView foot = CaseViews.text(this,
                "Powered By : Soolegal Technologies Pvt. Ltd.\nDeveloped By : Sun Integrated Technologies & Applications",
                13, AppTheme.text(this), false);
        foot.setGravity(Gravity.CENTER);
        root.addView(foot, CaseViews.lp(this, 28, 8));

        sv.addView(root);
        setContentView(sv);
    }

    private LinearLayout field(int iconRes, String hint, boolean password) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(LegalUi.dp(this, 16), LegalUi.dp(this, 4), LegalUi.dp(this, 16), LegalUi.dp(this, 4));
        row.setBackground(CaseViews.box(this, AppTheme.card(this), 14, AppTheme.fieldStroke(this), 1));
        row.addView(CaseViews.icon(this, iconRes, AppTheme.text(this), 26));
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setBackground(null);
        e.setTextSize(16);
        e.setPadding(LegalUi.dp(this, 16), LegalUi.dp(this, 14), 0, LegalUi.dp(this, 14));
        if (password) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        row.addView(e, LegalUi.weight());
        return row;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
