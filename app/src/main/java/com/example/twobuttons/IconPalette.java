package com.example.twobuttons;

import android.content.Context;
import java.util.HashMap;
import java.util.Map;

/**
 * One colour per icon, used app-wide. CaseViews.icon swaps in these colours whenever the caller
 * asked for a plain theme colour (text / sub / accent); explicit colours (white on a filled
 * button, red, green) and navigation arrows are left alone.
 */
final class IconPalette {
    private static Map<Integer, int[]> map;

    private static void put(int res, int light, int dark) {
        map.put(res, new int[]{light, dark});
    }

    private static synchronized Map<Integer, int[]> map() {
        if (map != null) return map;
        map = new HashMap<>();
        put(R.drawable.ic_home, 0xFF1565C0, 0xFF64B5F6);
        put(R.drawable.ic_person, 0xFF6A1B9A, 0xFFCE93D8);
        put(R.drawable.ic_person_add, 0xFF00897B, 0xFF4DB6AC);
        put(R.drawable.ic_contacts, 0xFF5E35B1, 0xFFB39DDB);
        put(R.drawable.ic_calendar, 0xFFE65100, 0xFFFFB74D);
        put(R.drawable.ic_bell, 0xFFFFA000, 0xFFFFD54F);
        put(R.drawable.ic_lock, 0xFF546E7A, 0xFF90A4AE);
        put(R.drawable.ic_filter, 0xFF00838F, 0xFF4DD0E1);
        put(R.drawable.ic_book, 0xFF6D4C41, 0xFFBCAAA4);
        put(R.drawable.ic_mail, 0xFFD84315, 0xFFFF8A65);
        put(R.drawable.ic_phone, 0xFF2E7D32, 0xFF81C784);
        put(R.drawable.ic_call, 0xFF2E7D32, 0xFF81C784);
        put(R.drawable.ic_search, 0xFF1976D2, 0xFF64B5F6);
        put(R.drawable.ic_tag, 0xFFAD1457, 0xFFF48FB1);
        put(R.drawable.ic_shield, 0xFF00695C, 0xFF4DB6AC);
        put(R.drawable.ic_share, 0xFF0277BD, 0xFF4FC3F7);
        put(R.drawable.ic_notes, 0xFFF57F17, 0xFFFFD54F);
        put(R.drawable.ic_image, 0xFF2E7D32, 0xFF81C784);
        put(R.drawable.ic_star, 0xFFF9A825, 0xFFFFD54F);
        put(R.drawable.ic_upload, 0xFF1565C0, 0xFF64B5F6);
        put(R.drawable.ic_download, 0xFF00796B, 0xFF4DB6AC);
        put(R.drawable.ic_pdf, 0xFFD32F2F, 0xFFEF9A9A);
        put(R.drawable.ic_word, 0xFF1565C0, 0xFF64B5F6);
        put(R.drawable.ic_briefcase, 0xFF795548, 0xFFA1887F);
        put(R.drawable.ic_bag, 0xFFEF6C00, 0xFFFFB74D);
        put(R.drawable.ic_check_circle, 0xFF2E7D32, 0xFF81C784);
        put(R.drawable.ic_check, 0xFF2E7D32, 0xFF81C784);
        put(R.drawable.ic_mic, 0xFFC62828, 0xFFEF9A9A);
        put(R.drawable.ic_refresh, 0xFF0097A7, 0xFF4DD0E1);
        put(R.drawable.ic_camera, 0xFF6A1B9A, 0xFFBA68C8);
        put(R.drawable.ic_delete, 0xFFD32F2F, 0xFFEF9A9A);
        put(R.drawable.ic_info, 0xFF0288D1, 0xFF4FC3F7);
        put(R.drawable.ic_add_circle, 0xFF2E7D32, 0xFF81C784);
        put(R.drawable.ic_remove_circle, 0xFFD32F2F, 0xFFEF9A9A);
        put(R.drawable.ic_edit, 0xFFEF6C00, 0xFFFFB74D);
        put(R.drawable.ic_settings, 0xFF455A64, 0xFF90A4AE);
        put(R.drawable.ic_advsearch, 0xFF3949AB, 0xFF9FA8DA);
        put(R.drawable.ic_replace, 0xFF00897B, 0xFF4DB6AC);
        put(R.drawable.ic_card, 0xFF7B1FA2, 0xFFCE93D8);
        put(R.drawable.ic_court, 0xFFB7791F, 0xFFF0B03A);
        put(R.drawable.ic_map, 0xFF2E7D32, 0xFF81C784);
        put(R.drawable.ic_location, 0xFFD32F2F, 0xFFEF9A9A);
        put(R.drawable.ic_award, 0xFFF9A825, 0xFFFFD54F);
        put(R.drawable.ic_eye, 0xFF1565C0, 0xFF64B5F6);
        put(R.drawable.ic_scanner, 0xFF00796B, 0xFF4DB6AC);
        put(R.drawable.ic_marked, 0xFFC2185B, 0xFFF48FB1);
        // not listed on purpose (stay plain): close, chevron_right, arrow_back, arrow_outward,
        // expand_more, menu, drag
        return map;
    }

    /** The icon's own colour when the caller passed a plain theme colour; otherwise {@code asked}. */
    static int color(Context c, int res, int asked) {
        boolean plain = asked == AppTheme.text(c) || asked == AppTheme.sub(c)
                || asked == AppTheme.accent(c) || asked == AppTheme.navOff(c);
        if (!plain) return asked;
        int[] p = map().get(res);
        if (p == null) return asked;
        return AppTheme.isDark(c) ? p[1] : p[0];
    }

    /** Light or dark variant of a colour pair for the current theme. */
    static int pick(Context c, int light, int dark) {
        return AppTheme.isDark(c) ? dark : light;
    }
}
