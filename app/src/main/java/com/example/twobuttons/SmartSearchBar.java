package com.example.twobuttons;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;




/**
 * The app's search bar: magnifier, text box, then mic, camera and scanner icons on the right.
 * The three icons are placeholders for now: tapping them does nothing.
 */
final class SmartSearchBar {

    private final Activity a;
    private final LinearLayout frame;
    private final RoundEdit edit;

    SmartSearchBar(Activity a, String hint) {
        this.a = a;
        frame = new LinearLayout(a);
        frame.setOrientation(LinearLayout.HORIZONTAL);
        frame.setGravity(Gravity.CENTER_VERTICAL);
        frame.setPadding(dp(10), 0, dp(4), 0);
        frame.setBackground(CaseViews.box(a, AppTheme.field(a), 10, AppTheme.fieldStroke(a), 1));

        frame.addView(CaseViews.icon(a, R.drawable.ic_search, AppTheme.sub(a), 18));

        edit = new RoundEdit(a);
        FieldStyle.hint(edit, hint);
        edit.setSingleLine(true);
        edit.setEllipsize(TextUtils.TruncateAt.END);
        edit.setTextSize(13);
        edit.setBackground(null);
        edit.setPadding(dp(8), dp(8), 0, dp(8));
        frame.addView(edit, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        frame.addView(tool(R.drawable.ic_mic, "Voice search", v -> { /* placeholder: does nothing yet */ }));
        frame.addView(tool(R.drawable.ic_camera, "Search from a photo of text", v -> { /* placeholder: does nothing yet */ }));
        frame.addView(tool(R.drawable.ic_scanner, "Scan QR code or barcode", v -> { /* placeholder: does nothing yet */ }));
    }

    View view() { return frame; }

    EditText edit() { return edit; }

    private int dp(int v) { return CaseViews.dp(a, v); }

    private ImageView tool(int icon, String description, View.OnClickListener click) {
        ImageView v = CaseViews.icon(a, icon, AppTheme.accent(a), 20);
        v.setPadding(dp(8), dp(8), dp(8), dp(8));
        v.setContentDescription(description);
        v.setOnClickListener(click);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
        return v;
    }

    /** The tools do nothing yet, so no result ever belongs to this bar. */
    boolean onActivityResult(int req, int res, Intent data) {
        return false;
    }
}
