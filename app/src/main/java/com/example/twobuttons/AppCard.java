package com.example.twobuttons;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The one card design used on every page.
 *
 * <pre>
 *  [avatar]  Title                      topRight big
 *            subtitle                   topRight small
 *            extra lines...
 *  (chip) (chip) (chip)
 *  ------------------------------------------------
 *  footer big                 [secondary] [primary]
 *  footer small
 * </pre>
 *
 * Every part is optional. Typical use:
 * <pre>
 *  View v = new AppCard(this)
 *          .avatarText("R").title("State vs Abhi").subtitle("Criminal | Bail")
 *          .chip("Needs update", AppCard.Tone.WARN)
 *          .footer("12 Oct 2026", "Next hearing")
 *          .secondary("Re-Schedule", v1 -> ...).primary("Details", v2 -> ...)
 *          .onClick(v3 -> ...)
 *          .build();
 * </pre>
 */
final class AppCard {

    enum Tone { NEUTRAL, ACCENT, GOOD, WARN, BAD }

    private static final class Chip {
        final String text;
        final Tone tone;
        final View.OnClickListener click;
        Chip(String t, Tone tone, View.OnClickListener c) { text = t; this.tone = tone; click = c; }
    }

    private final Context c;
    private String avatarText = "";
    private int avatarIcon = 0;
    private String title = "";
    private String subtitle = "";
    private final List<String> lines = new ArrayList<>();
    private String topBig = "";
    private String topSmall = "";
    private View topView;
    private final List<Chip> chips = new ArrayList<>();
    private String footBig = "";
    private String footSmall = "";
    private int footColor = 0;
    private String secondaryLabel = "";
    private View.OnClickListener secondaryClick;
    private String primaryLabel = "";
    private View.OnClickListener primaryClick;
    private View.OnClickListener cardClick;
    private boolean compact = false;

    AppCard(Context c) { this.c = c; }

    // ------------------------------------------------------------ content

    AppCard avatarText(String s) { avatarText = s == null ? "" : s; return this; }
    AppCard avatarIcon(int res) { avatarIcon = res; return this; }
    AppCard title(String s) { title = s == null ? "" : s; return this; }
    AppCard subtitle(String s) { subtitle = s == null ? "" : s; return this; }
    /** Extra small grey line under the subtitle. Empty text is ignored. */
    AppCard line(String s) { if (s != null && !s.isEmpty()) lines.add(s); return this; }
    /** Right side of the top row: a bold value and a small caption under it. */
    AppCard topRight(String big, String small) {
        topBig = big == null ? "" : big;
        topSmall = small == null ? "" : small;
        return this;
    }
    /** Replaces the top-right text with any view (for example a star icon). */
    AppCard topRightView(View v) { topView = v; return this; }
    AppCard chip(String text, Tone tone) { return chip(text, tone, null); }
    AppCard chip(String text, Tone tone, View.OnClickListener click) {
        if (text != null && !text.isEmpty()) chips.add(new Chip(text, tone, click));
        return this;
    }
    AppCard footer(String big, String small) { return footer(big, small, 0); }
    AppCard footer(String big, String small, int bigColor) {
        footBig = big == null ? "" : big;
        footSmall = small == null ? "" : small;
        footColor = bigColor;
        return this;
    }
    AppCard secondary(String label, View.OnClickListener l) {
        secondaryLabel = label; secondaryClick = l; return this;
    }
    AppCard primary(String label, View.OnClickListener l) {
        primaryLabel = label; primaryClick = l; return this;
    }
    AppCard onClick(View.OnClickListener l) { cardClick = l; return this; }
    /** Smaller padding, text and buttons, for cards nested inside another card. */
    AppCard compact() { compact = true; return this; }

    // ------------------------------------------------------------ shared look

    /** Empty rounded white card with the shared padding, stroke and soft shadow. */
    static LinearLayout surface(Context c) { return surface(c, false); }

    static LinearLayout surface(Context c, boolean compact) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int pad = compact ? 10 : 16;
        l.setPadding(dp(c, pad), dp(c, pad), dp(c, pad), dp(c, pad));
        l.setBackground(CaseViews.box(c, AppTheme.field(c), compact ? 12 : 18, AppTheme.cardStroke(c), 1));
        l.setElevation(dp(c, compact ? 1 : 2));
        return l;
    }

    /** Rounded chip, also usable on its own. */
    static TextView chipView(Context c, String text, Tone tone) { return chipView(c, text, tone, false); }

    static TextView chipView(Context c, String text, Tone tone, boolean compact) {
        TextView t = CaseViews.text(c, text, compact ? 11 : 12, toneText(c, tone), false);
        t.setSingleLine(true);
        t.setPadding(dp(c, compact ? 9 : 12), dp(c, compact ? 3 : 5), dp(c, compact ? 9 : 12), dp(c, compact ? 3 : 5));
        t.setBackground(CaseViews.box(c, toneFill(c, tone), 14, 0, 0));
        return t;
    }

    private static int toneFill(Context c, Tone t) {
        boolean d = AppTheme.isDark(c);
        switch (t) {
            case ACCENT: return d ? 0xFF2A2C54 : 0xFFE8EAFF;
            case GOOD:   return d ? 0xFF1F3A25 : 0xFFD8F3DC;
            case WARN:   return d ? 0xFF3A2F18 : 0xFFFFF1D6;
            case BAD:    return d ? 0xFF3A2220 : 0xFFFDE4E0;
            default:     return d ? 0xFF2A2C3A : 0xFFF1F2F6;
        }
    }

    private static int toneText(Context c, Tone t) {
        boolean d = AppTheme.isDark(c);
        switch (t) {
            case ACCENT: return AppTheme.accent(c);
            case GOOD:   return d ? 0xFF81C784 : 0xFF2E7D32;
            case WARN:   return d ? 0xFFFFC857 : 0xFF9A6200;
            case BAD:    return d ? 0xFFFF8A80 : 0xFFC62828;
            default:     return AppTheme.sub(c);
        }
    }

    private static int dp(Context c, int v) { return CaseViews.dp(c, v); }

    // ------------------------------------------------------------ build

    View build() {
        LinearLayout card = surface(c, compact);

        // top row: avatar, text column, right value
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.TOP);

        if (avatarIcon != 0 || !avatarText.isEmpty()) {
            LinearLayout av = new LinearLayout(c);
            av.setGravity(Gravity.CENTER);
            av.setBackground(CaseViews.box(c, toneFill(c, Tone.ACCENT), compact ? 10 : 14, 0, 0));
            if (avatarIcon != 0) {
                av.addView(CaseViews.icon(c, avatarIcon, AppTheme.accent(c), compact ? 20 : 26));
            } else {
                av.addView(CaseViews.text(c, avatarText, compact ? 15 : 22, AppTheme.accent(c), true));
            }
            top.addView(av, new LinearLayout.LayoutParams(dp(c, compact ? 38 : 56), dp(c, compact ? 38 : 56)));
        }

        LinearLayout names = new LinearLayout(c);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(avatarIcon != 0 || !avatarText.isEmpty() ? dp(c, compact ? 10 : 12) : 0, 0, dp(c, 8), 0);
        if (!title.isEmpty()) {
            TextView t = CaseViews.text(c, title, compact ? 14 : 17, AppTheme.text(c), true);
            t.setMaxLines(2);
            t.setEllipsize(TextUtils.TruncateAt.END);
            names.addView(t);
        }
        if (!subtitle.isEmpty()) {
            TextView s = CaseViews.text(c, subtitle, compact ? 12 : 13, AppTheme.text(c), false);
            s.setPadding(0, dp(c, 2), 0, 0);
            names.addView(s);
        }
        for (String l : lines) {
            TextView m = CaseViews.text(c, l, compact ? 11 : 12, AppTheme.sub(c), false);
            m.setPadding(0, dp(c, 2), 0, 0);
            names.addView(m);
        }
        top.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (topView != null) {
            top.addView(topView);
        } else if (!topBig.isEmpty() || !topSmall.isEmpty()) {
            LinearLayout right = new LinearLayout(c);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            if (!topBig.isEmpty()) right.addView(CaseViews.text(c, topBig, 15, AppTheme.text(c), true));
            if (!topSmall.isEmpty()) right.addView(CaseViews.text(c, topSmall, 12, AppTheme.sub(c), false));
            top.addView(right);
        }
        card.addView(top);

        // chips
        if (!chips.isEmpty()) {
            Flow flow = new Flow(c, dp(c, 8), dp(c, 8));
            for (Chip ch : chips) {
                TextView v = chipView(c, ch.text, ch.tone, compact);
                if (ch.click != null) v.setOnClickListener(ch.click);
                flow.addView(v);
            }
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            flp.topMargin = dp(c, compact ? 8 : 12);
            card.addView(flow, flp);
        }

        // footer: value on the left, buttons on the right
        boolean hasFoot = !footBig.isEmpty() || !footSmall.isEmpty();
        boolean hasBtn = !secondaryLabel.isEmpty() || !primaryLabel.isEmpty();
        if (hasFoot || hasBtn) {
            View line = new View(c);
            line.setBackgroundColor(AppTheme.cardStroke(c));
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1);
            llp.topMargin = dp(c, compact ? 8 : 14);
            llp.bottomMargin = dp(c, compact ? 8 : 12);
            card.addView(line, llp);

            LinearLayout foot = new LinearLayout(c);
            foot.setOrientation(LinearLayout.HORIZONTAL);
            foot.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout left = new LinearLayout(c);
            left.setOrientation(LinearLayout.VERTICAL);
            if (!footBig.isEmpty()) {
                TextView fb = CaseViews.text(c, footBig, compact ? 13 : 16, footColor != 0 ? footColor : AppTheme.text(c), true);
                fb.setMaxLines(2);
                fb.setEllipsize(TextUtils.TruncateAt.END);
                left.addView(fb);
            }
            if (!footSmall.isEmpty()) left.addView(CaseViews.text(c, footSmall, compact ? 11 : 12, AppTheme.sub(c), false));
            foot.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (!secondaryLabel.isEmpty()) {
                Button b = button(secondaryLabel, false);
                b.setOnClickListener(secondaryClick);
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, compact ? 30 : 40));
                blp.leftMargin = dp(c, 8);
                foot.addView(b, blp);
            }
            if (!primaryLabel.isEmpty()) {
                Button b = button(primaryLabel, true);
                b.setOnClickListener(primaryClick);
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(c, compact ? 30 : 40));
                blp.leftMargin = dp(c, 8);
                foot.addView(b, blp);
            }
            card.addView(foot);
        }

        if (cardClick != null) card.setOnClickListener(cardClick);
        return card;
    }

    private Button button(String label, boolean filled) {
        boolean dark = AppTheme.isDark(c);
        int fill = filled ? (dark ? 0xFFEDEDF2 : 0xFF0B1F1A) : Color.TRANSPARENT;
        int text = filled ? (dark ? 0xFF000000 : 0xFFFFFFFF) : AppTheme.text(c);
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(compact ? 12 : 14);
        b.setTextColor(text);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(c, compact ? 12 : 18), 0, dp(c, compact ? 12 : 18), 0);
        b.setStateListAnimator(null);
        b.setBackground(CaseViews.box(c, fill, compact ? 15 : 20, filled ? 0 : AppTheme.cardStroke(c), filled ? 0 : 1));
        return b;
    }

    /** Minimal wrapping row so chips move to a new line instead of running off the card. */
    static final class Flow extends ViewGroup {
        private final int hGap;
        private final int vGap;

        Flow(Context c, int hGap, int vGap) {
            super(c);
            this.hGap = hGap;
            this.vGap = vGap;
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int maxW = MeasureSpec.getSize(wSpec);
            int x = 0, y = 0, rowH = 0, usedW = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                measureChild(v, MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST),
                        MeasureSpec.UNSPECIFIED);
                int w = v.getMeasuredWidth();
                int h = v.getMeasuredHeight();
                if (x > 0 && x + w > maxW) {
                    x = 0;
                    y += rowH + vGap;
                    rowH = 0;
                }
                x += w + hGap;
                rowH = Math.max(rowH, h);
                usedW = Math.max(usedW, x - hGap);
            }
            setMeasuredDimension(resolveSize(usedW, wSpec), resolveSize(y + rowH, hSpec));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int maxW = r - l;
            int x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View v = getChildAt(i);
                int w = v.getMeasuredWidth();
                int h = v.getMeasuredHeight();
                if (x > 0 && x + w > maxW) {
                    x = 0;
                    y += rowH + vGap;
                    rowH = 0;
                }
                v.layout(x, y, x + w, y + h);
                x += w + hGap;
                rowH = Math.max(rowH, h);
            }
        }
    }
}
