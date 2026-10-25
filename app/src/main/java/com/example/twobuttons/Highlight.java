package com.example.twobuttons;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import java.util.Locale;

/** Colours every occurrence of the search text inside a label (orange marker, dark text). */
final class Highlight {
    static final int BG = 0xFFFFB74D;
    static final int FG = 0xFF000000;

    private Highlight() {}

    static CharSequence apply(String text, String query) {
        if (text == null) return "";
        if (query == null) return text;
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return text;
        String low = text.toLowerCase(Locale.ROOT);
        if (low.length() != text.length()) return text;      // odd case mapping: keep offsets safe
        SpannableString s = new SpannableString(text);
        int from = 0;
        boolean any = false;
        while (true) {
            int at = low.indexOf(q, from);
            if (at < 0) break;
            int end = at + q.length();
            s.setSpan(new BackgroundColorSpan(BG), at, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new ForegroundColorSpan(FG), at, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new StyleSpan(Typeface.BOLD), at, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            any = true;
            from = end;
        }
        return any ? s : text;
    }
}
