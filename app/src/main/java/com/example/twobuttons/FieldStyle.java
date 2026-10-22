package com.example.twobuttons;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * THE one definition of how a text box and a dropdown look in this app. Height, corner radius,
 * font size and face, inner padding, border widths and colours, hint (placeholder) colour all live
 * here and nowhere else. RoundEdit, RoundAuto, RoundSpinner and the New-Case style dropdown box all
 * read from this class, so changing a number here changes every control in the app.
 * Reference look = Add New Case page.
 */
final class FieldStyle {

    private FieldStyle() { }

    static final int HEIGHT_DP = 52;          // every single-line box and every dropdown
    static final int RADIUS_DP = 14;
    static final float TEXT_SP = 15;
    static final int PAD_H_DP = 16;
    static final int PAD_V_DP = 12;
    static final int STROKE_DP = 1;           // normal border
    static final int STROKE_ACTIVE_DP = 2;    // focused / invalid border
    static final int DROPDOWN_PAD_H_DP = 14;
    static final int ARROW_DP = 22;
    static final int RED = 0xFFD93025;

    static int textColor(Context c) { return AppTheme.text(c); }

    /** Placeholder / hint colour, same for text boxes and unselected dropdowns. */
    static int hintColor(Context c) { return AppTheme.sub(c); }

    static int fill(Context c) { return AppTheme.field(c); }

    /** Black (white in dark theme) normally, accent while focused, red when invalid. */
    static int borderColor(Context c, boolean focus, boolean invalid) {
        return invalid ? RED : focus ? AppTheme.accent(c) : AppTheme.text(c);
    }

    static GradientDrawable border(Context c, boolean focus, boolean invalid) {
        boolean thick = focus || invalid;
        return CaseViews.box(c, fill(c), RADIUS_DP, borderColor(c, focus, invalid),
                thick ? STROKE_ACTIVE_DP : STROKE_DP);
    }

    static int heightPx(Context c) { return CaseViews.dp(c, HEIGHT_DP); }

    /** Texts that mean "nothing chosen yet" are drawn in the hint colour. */
    static boolean isPlaceholder(CharSequence s) {
        String t = s == null ? "" : s.toString();
        return t.isEmpty() || t.startsWith("--") || t.startsWith("Select") || t.equals("All") || t.startsWith("All ");
    }

    /** Typed text of a text box: size, face, colours, padding, gravity. */
    static void applyEditText(EditText e) {
        Context c = e.getContext();
        e.setTextColor(textColor(c));
        e.setHintTextColor(hintColor(c));
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP);
        e.setTypeface(Typeface.DEFAULT);
        e.setIncludeFontPadding(false);
        e.setPadding(CaseViews.dp(c, PAD_H_DP), CaseViews.dp(c, PAD_V_DP), CaseViews.dp(c, PAD_H_DP), CaseViews.dp(c, PAD_V_DP));
        e.setGravity(Gravity.CENTER_VERTICAL);
    }

    /** Closed-dropdown text: same size and face as a text box, hint colour while nothing is chosen. */
    static void applyDropdownText(TextView t, boolean placeholder) {
        Context c = t.getContext();
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP);
        t.setTypeface(Typeface.DEFAULT);
        t.setIncludeFontPadding(false);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setTextColor(placeholder ? hintColor(c) : textColor(c));
    }

    /**
     * A tap-to-pick box (text + arrow) with the shared height and border. Returns its TextView;
     * the box itself is that view's tag.
     */
    static TextView dropdown(Context c, LinearLayout parent, String placeholder,
                             View.OnClickListener click, LinearLayout.LayoutParams lp) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(CaseViews.dp(c, DROPDOWN_PAD_H_DP), 0, CaseViews.dp(c, DROPDOWN_PAD_H_DP), 0);
        box.setBackground(border(c, false, false));
        box.setMinimumHeight(heightPx(c));
        TextView t = new TextView(c);
        t.setText(placeholder);
        applyDropdownText(t, true);
        t.setTag(box);
        box.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(CaseViews.icon(c, R.drawable.ic_expand_more, textColor(c), ARROW_DP));
        box.setOnClickListener(click);
        lp.height = heightPx(c);
        parent.addView(box, lp);
        return t;
    }

    static void setDropdownValue(TextView t, String value, String placeholder) {
        boolean has = value != null && !value.isEmpty();
        t.setText(has ? value : placeholder);
        t.setTextColor(has ? textColor(t.getContext()) : hintColor(t.getContext()));
    }

    static void setDropdownInvalid(TextView t, boolean invalid) {
        if (t != null && t.getTag() instanceof View) {
            ((View) t.getTag()).setBackground(border(t.getContext(), false, invalid));
        }
    }
}
