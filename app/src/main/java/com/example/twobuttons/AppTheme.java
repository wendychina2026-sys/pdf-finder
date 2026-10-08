package com.example.twobuttons;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** Light / Dark / System default. One place for theme mode + colors. */
final class AppTheme {

    static final String LIGHT = "light";
    static final String DARK = "dark";
    static final String SYSTEM = "system";

    private AppTheme() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
    }

    static String mode(Context c) {
        return prefs(c).getString("theme", LIGHT);
    }

    static void setMode(Context c, String mode) {
        prefs(c).edit().putString("theme", mode).apply();
    }

    static boolean isDark(Context c) {
        String m = mode(c);
        if (DARK.equals(m)) return true;
        if (LIGHT.equals(m)) return false;
        int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Call before super.onCreate(). */
    static void apply(Activity a) {
        a.setTheme(isDark(a)
                ? android.R.style.Theme_DeviceDefault
                : android.R.style.Theme_DeviceDefault_Light);
    }

    // ---- palette ----
    static int bg(Context c)        { return isDark(c) ? 0xFF121212 : 0xFFFFFFFF; }
    static int text(Context c)      { return isDark(c) ? 0xFFEDEDF2 : 0xFF000000; }
    static int sub(Context c)       { return isDark(c) ? 0xFFA6A9B8 : 0xFF6B6F80; }
    static int card(Context c)      { return isDark(c) ? 0xFF1E1F2B : 0xFFF4F5FA; }
    static int cardStroke(Context c){ return isDark(c) ? 0xFF343647 : 0xFFE3E5F0; }
    static int field(Context c)     { return isDark(c) ? 0xFF1E1F2B : 0xFFFFFFFF; }
    static int fieldStroke(Context c){ return isDark(c) ? 0xFF3A3C4E : 0xFFD9DBE6; }
    static int accent(Context c)    { return isDark(c) ? 0xFFA9B1FF : 0xFF0B0B8E; }
    static int navBg(Context c)     { return isDark(c) ? 0xFF1B1C28 : 0xFFF4F5FA; }
    static int navOff(Context c)    { return isDark(c) ? 0xFFC5C7D6 : 0xFF333344; }
    static int topics(Context c)    { return isDark(c) ? 0xFF81C784 : 0xFF2E7D32; }
    static int info(Context c)      { return isDark(c) ? 0xFF90CAF9 : 0xFF1565C0; }
}
