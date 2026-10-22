package com.example.twobuttons;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewParent;
import android.widget.EditText;

import java.util.function.Consumer;

/**
 * Behaviour shared by RoundEdit and RoundAuto: border follows focus / invalid, red clears when the
 * user types, single line = FieldStyle height, "frame" mode for icon + box rows.
 * After install() the host ignores outside attempts to restyle it (see the host's overrides).
 */
final class FieldState {

    private final EditText host;
    private final Consumer<Drawable> setSelf;     // host's super.setBackground
    boolean locked;
    private boolean invalid, required, inFrame;
    private View frame;
    private int minPx;

    FieldState(EditText host, Consumer<Drawable> setSelf) {
        this.host = host;
        this.setSelf = setSelf;
    }

    void install() {
        Context c = host.getContext();
        FieldStyle.applyEditText(host);
        minPx = FieldStyle.heightPx(c);
        host.setMinHeight(minPx);
        host.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void afterTextChanged(Editable s) {
                if (invalid && s.length() > 0) setInvalid(false);
            }
        });
        paint();
        locked = true;
    }

    boolean singleLineFixed() { return host.getMaxLines() == 1; }

    int minPx() { return minPx; }

    void setInvalid(boolean bad) {
        invalid = bad;
        paint();
    }

    boolean isInvalid() { return invalid; }

    void setRequired(boolean r) { required = r; }

    boolean requireFilled() {
        boolean ok = host.getText().toString().trim().length() > 0;
        setInvalid(!ok);
        if (!ok) host.requestFocus();
        return ok;
    }

    boolean inFrame() { return inFrame; }

    void enterFrame() {
        inFrame = true;
        setSelf.accept(null);
        bindFrame();
    }

    void onAttached() { if (inFrame) bindFrame(); }

    void onFocus() {
        if (!host.isFocused() && required && host.getText().toString().trim().isEmpty()) invalid = true;
        paint();
    }

    private void bindFrame() {
        ViewParent p = host.getParent();
        if (p instanceof View && ((View) p).getBackground() != null) {
            frame = (View) p;
            minPx = Math.max(0, FieldStyle.heightPx(host.getContext()) - frame.getPaddingTop() - frame.getPaddingBottom());
            host.setMinHeight(minPx);
            paint();
        }
    }

    private void paint() {
        Drawable g = FieldStyle.border(host.getContext(), host.isFocused(), invalid);
        if (inFrame) {
            if (frame != null) frame.setBackground(g);
        } else {
            setSelf.accept(g);
        }
    }
}
