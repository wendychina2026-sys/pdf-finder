package com.example.twobuttons;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
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
