package com.example.twobuttons;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import android.widget.AutoCompleteTextView;

/**
 * Text box with suggestions (e.g. Lawyer name): exactly the RoundEdit look and behaviour.
 * All numbers and colours come from FieldStyle; the look is locked (see FieldState).
 */
public class RoundAuto extends AutoCompleteTextView {

    static final int RED = FieldStyle.RED;
    static final int HEIGHT_DP = FieldStyle.HEIGHT_DP;

    public RoundAuto(Context c) {
        super(c);
        st = new FieldState(this, d -> super.setBackground(d));
        st.install();
    }

    private FieldState st;

    /** Single-line box = exactly the shared height, the same number RoundSpinner and the dropdown boxes use. */
    @Override
    protected void onMeasure(int w, int h) {
        super.onMeasure(w, h);
        if (st != null && getMaxLines() == 1) setMeasuredDimension(getMeasuredWidth(), st.minPx());
    }

    /** Mark / unmark as failed validation (red border). */
    public void setInvalid(boolean bad) { st.setInvalid(bad); }

    public boolean isInvalid() { return st.isInvalid(); }

    /** Mandatory box: turns red when the user leaves it empty. Returns this for chaining. */
    public RoundAuto setRequired(boolean r) {
        st.setRequired(r);
        return this;
    }

    /** Red border + focus when the value is empty. Returns true when the value is fine. */
    public boolean requireFilled() { return st.requireFilled(); }

    // ---- locked look: outside callers cannot restyle the box (one look app-wide) ----

    @Override
    public void setTextSize(int unit, float size) {
        super.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, FieldStyle.TEXT_SP);
    }

    @Override
    public void setMinLines(int n) {
        if (st == null) { super.setMinLines(n); return; }
        if (n <= 1) setMinHeight(st.minPx());
        else super.setMinLines(n);
    }

    @Override
    public void setBackground(Drawable d) {
        if (st == null) { super.setBackground(d); return; }
        if (d == null) st.enterFrame();      // "I sit inside a frame": the frame gets the border
        // non-null: ignored
    }

    /** Padding is fixed, except inside an icon + box frame row where the row decides. */
    @Override
    public void setPadding(int l, int t, int r, int b) {
        if (st == null || !st.locked || st.inFrame()) super.setPadding(l, t, r, b);
    }

    @Override public void setTypeface(android.graphics.Typeface tf) { if (st == null || !st.locked) super.setTypeface(tf); }
    @Override public void setTypeface(android.graphics.Typeface tf, int style) { if (st == null || !st.locked) super.setTypeface(tf, style); }
    @Override public void setTextColor(int color) { if (st == null || !st.locked) super.setTextColor(color); }
    @Override public void setTextColor(android.content.res.ColorStateList colors) { if (st == null || !st.locked) super.setTextColor(colors); }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (st != null) st.onAttached();
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect prev) {
        super.onFocusChanged(focused, direction, prev);
        if (st != null) st.onFocus();
    }
}
