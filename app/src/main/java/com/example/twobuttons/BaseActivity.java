package com.example.twobuttons;

import android.app.Activity;
import android.os.Bundle;

/** Applies the chosen theme. Recreates itself if the theme changed while away. */
public abstract class BaseActivity extends Activity {

    private boolean createdDark;

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
