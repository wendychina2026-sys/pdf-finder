package com.example.twobuttons;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Every option list in the app: a round box that opens the round-card option sheet.
 * Still a Spinner, so adapters, setSelection and OnItemSelectedListener work unchanged.
 */
public class RoundSpinner extends Spinner {

    private String title = "Select";

    public RoundSpinner(Context c) {
        super(c);
        Drawable arrow = c.getDrawable(R.drawable.ic_expand_more).mutate();
        arrow.setTint(FieldStyle.textColor(c));
        LayerDrawable ld = new LayerDrawable(new Drawable[]{
                FieldStyle.border(c, false, false), arrow});
        ld.setLayerSize(1, CaseViews.dp(c, FieldStyle.ARROW_DP), CaseViews.dp(c, FieldStyle.ARROW_DP));
        ld.setLayerGravity(1, Gravity.END | Gravity.CENTER_VERTICAL);
        ld.setLayerInsetRight(1, CaseViews.dp(c, 14));
        setBackground(ld);
        setPadding(CaseViews.dp(c, 6), CaseViews.dp(c, 8), CaseViews.dp(c, 44), CaseViews.dp(c, 8));   // + 8dp text pad = 14dp, same as New Case dropdowns
        setMinimumHeight(FieldStyle.heightPx(c));
    }

    /**
     * One size for every dropdown in the app: exactly the text box height, full width.
     * Layout params that ask for wrap_content (or a different height) are overridden here.
     */
    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int h = FieldStyle.heightPx(getContext());
        int w = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED
                ? widthSpec
                : MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthSpec), MeasureSpec.EXACTLY);
        super.onMeasure(w, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        setMeasuredDimension(getMeasuredWidth(), h);
    }

    /** Closed box text: same 15sp, one line, same inner padding for every dropdown. */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        android.view.View v = getChildCount() > 0 ? getChildAt(0) : null;
        if (v instanceof android.widget.TextView) {
            android.widget.TextView tv = (android.widget.TextView) v;
            Context c = getContext();
            int pad = CaseViews.dp(c, 8);
            boolean holder = FieldStyle.isPlaceholder(tv.getText());
            int wantColor = holder ? FieldStyle.hintColor(c) : FieldStyle.textColor(c);
            if (tv.getCurrentTextColor() != wantColor) tv.setTextColor(wantColor);
            float want = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                    FieldStyle.TEXT_SP, c.getResources().getDisplayMetrics());
            if (tv.getTypeface() != android.graphics.Typeface.DEFAULT || Math.abs(tv.getTextSize() - want) > 0.5f
                    || tv.getPaddingLeft() != pad || tv.getEllipsize() != android.text.TextUtils.TruncateAt.END) {
                FieldStyle.applyDropdownText(tv, holder);
                tv.setPadding(pad, 0, 0, 0);
                tv.setGravity(Gravity.CENTER_VERTICAL);
            }
        }
    }

    /** Heading of the option sheet. */
    public RoundSpinner setTitle(String t) {
        title = t;
        return this;
    }

    @Override
    public boolean performClick() {
        SpinnerAdapter ad = getAdapter();
        if (ad == null || ad.getCount() == 0) return true;
        Context ctx = getContext();
        while (ctx instanceof ContextWrapper && !(ctx instanceof Activity)) {
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        if (!(ctx instanceof Activity)) return super.performClick();
        List<String> items = new ArrayList<>();
        for (int i = 0; i < ad.getCount(); i++) items.add(String.valueOf(ad.getItem(i)));
        PickerDialogs.optionSheet((Activity) ctx, title, null, items, getSelectedItemPosition(),
                i -> setSelection(i));
        return true;
    }
}
