package com.example.twobuttons;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.widget.EditText;

/**
 * The one text box design for the whole app: round outline, hint (placeholder) while empty.
 * Border: black (white in dark theme) normally, accent colour while focused, red when invalid.
 * Invalid clears when the user types.
 *
 * Any code that sets its own non-null background on a RoundEdit is ignored, so every box looks
 * the same. setBackground(null) means "I sit inside a frame (icon + box row)": the parent frame
 * then gets the same round border and follows focus / invalid instead.
 */
public class RoundEdit extends EditText {

    static final int RED = 0xFFD93025;
    private static final int RADIUS_DP = 14;
    static final int HEIGHT_DP = 52;       // every single-line box is this tall
    private static final float TEXT_SP = 15;

    private int minPx;

    private boolean invalid;
    private boolean required;
    private boolean inFrame;
    private View frame;

    public RoundEdit(Context c) {
        super(c);
        setTextColor(AppTheme.text(c));
        setHintTextColor(AppTheme.sub(c));
        int h = CaseViews.dp(c, 16), v = CaseViews.dp(c, 12);
        setPadding(h, v, h, v);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP);
        setGravity(Gravity.CENTER_VERTICAL);
        minPx = CaseViews.dp(c, HEIGHT_DP);
        setMinHeight(minPx);
        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void afterTextChanged(Editable s) {
                if (invalid && s.length() > 0) setInvalid(false);
            }
        });
        paint();
    }

    /** Mark / unmark as failed validation (red border). */
    public void setInvalid(boolean bad) {
        invalid = bad;
        paint();
    }

    public boolean isInvalid() { return invalid; }

    /** Mandatory box: turns red when the user leaves it empty. Returns this for chaining. */
    public RoundEdit setRequired(boolean r) {
        required = r;
        return this;
    }

    /** Red border + focus when the value is empty. Returns true when the value is fine. */
    public boolean requireFilled() {
        boolean ok = getText().toString().trim().length() > 0;
        setInvalid(!ok);
        if (!ok) requestFocus();
        return ok;
    }

    /** One text size for every box, so heights match. Callers' sizes are ignored. */
    @Override
    public void setTextSize(int unit, float size) {
        super.setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP);
    }

    /** One line = the shared box height. More lines (Notes, Address) grow as asked. */
    @Override
    public void setMinLines(int n) {
        if (n <= 1) setMinHeight(minPx);
        else super.setMinLines(n);
    }

    @Override
    public void setBackground(Drawable d) {
        if (d == null) {
            inFrame = true;
            super.setBackground(null);
            bindFrame();
        }
        // non-null: ignored, keeps the shared look (also hit by the framework's default underline)
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (inFrame) bindFrame();
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect prev) {
        super.onFocusChanged(focused, direction, prev);
        if (!focused && required && getText().toString().trim().isEmpty()) invalid = true;
        paint();
    }

    private void bindFrame() {
        ViewParent p = getParent();
        if (p instanceof View && ((View) p).getBackground() != null) {
            frame = (View) p;
            minPx = Math.max(0, CaseViews.dp(getContext(), HEIGHT_DP) - frame.getPaddingTop() - frame.getPaddingBottom());
            setMinHeight(minPx);
            paint();
        }
    }

    private void paint() {
        Context c = getContext();
        boolean focus = isFocused();
        int stroke = invalid ? RED : focus ? AppTheme.accent(c) : AppTheme.text(c);
        int w = (invalid || focus) ? 2 : 1;
        GradientDrawable g = new GradientDrawable();
        g.setColor(AppTheme.field(c));
        g.setCornerRadius(CaseViews.dp(c, RADIUS_DP));
        g.setStroke(CaseViews.dp(c, w), stroke);
        if (inFrame) {
            if (frame != null) frame.setBackground(g);
        } else {
            super.setBackground(g);
        }
    }
}
