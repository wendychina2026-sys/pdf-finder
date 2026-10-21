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
        arrow.setTint(AppTheme.text(c));
        LayerDrawable ld = new LayerDrawable(new Drawable[]{
                CaseViews.box(c, AppTheme.field(c), 14, AppTheme.text(c), 1), arrow});
        ld.setLayerSize(1, CaseViews.dp(c, 22), CaseViews.dp(c, 22));
        ld.setLayerGravity(1, Gravity.END | Gravity.CENTER_VERTICAL);
        ld.setLayerInsetRight(1, CaseViews.dp(c, 14));
        setBackground(ld);
        setPadding(CaseViews.dp(c, 8), CaseViews.dp(c, 8), CaseViews.dp(c, 44), CaseViews.dp(c, 8));
        setMinimumHeight(CaseViews.dp(c, RoundEdit.HEIGHT_DP));
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
