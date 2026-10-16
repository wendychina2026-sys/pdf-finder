package com.example.twobuttons;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/** Applies the chosen theme. Recreates itself if the theme changed while away. */
public abstract class BaseActivity extends Activity {

    private boolean createdDark;

    /** Tab pages return their tab number (1 Find PDF, 2 Marked, 3 Search, 4 Settings); others -1. */
    protected int navIndex() { return -1; }

    @Override
    public void setContentView(View view) {
        int idx = navIndex();
        if (idx < 0) {
            super.setContentView(view);
            return;
        }
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(AppTheme.bg(this));
        page.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(BottomBar.tabs(this, idx, null));
        super.setContentView(page);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppTheme.apply(this);
        createdDark = AppTheme.isDark(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (AppTheme.isDark(this) != createdDark) recreate();
    }
}
