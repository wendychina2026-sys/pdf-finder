package com.example.twobuttons;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * White (theme-aware) bottom bar. The selected item lifts out of the bar as a
 * circle with its icon and label inside; the others sit flat on the bar.
 *
 * <pre>
 *  View bar = new BottomBar(this)
 *          .item(R.drawable.ic_home, "Home", v -> ...)
 *          .item(R.drawable.ic_pdf, "Find PDF", v -> ...)
 *          .build(0);          // 0 = index of the selected item
 * </pre>
 */
final class BottomBar {

    private static final int BAR_H = 60;      // dp, the bar itself
    private static final int CIRCLE = 72;     // dp, diameter of the raised circle (fits "Find PDF" / "Settings")
    private static final int TOP_EXTRA = 24;  // dp, how far the circle rises above the bar

    private static final class Entry {
        final int icon;
        final String label;
        final View.OnClickListener click;
        Entry(int icon, String label, View.OnClickListener click) {
            this.icon = icon; this.label = label; this.click = click;
        }
    }

    private final Context c;
    private final List<Entry> items = new ArrayList<>();

    BottomBar(Context c) { this.c = c; }

    /**
     * The app's five tabs. selected: 0 Home, 1 Find PDF, 2 Document Vault, 3 Search, 4 Settings.
     * Tapping another tab opens it; tab pages close themselves so Back returns to Home.
     */
    static View tabs(Activity a, int selected, View.OnClickListener onHome) {
        Class<?>[] pages = {MainActivity.class, FindPdfsActivity.class, MarkedFilesActivity.class,
                AdvancedSearchActivity.class, SettingsActivity.class};
        String[] labels = {"Home", "Find PDF", "Doc Vault", "Search", "Settings"};
        int[] icons = {R.drawable.ic_home, R.drawable.ic_pdf, R.drawable.ic_marked,
                R.drawable.ic_advsearch, R.drawable.ic_settings};
        BottomBar b = new BottomBar(a);
        for (int i = 0; i < pages.length; i++) {
            final int idx = i;
            View.OnClickListener click;
            if (i == selected) {
                click = i == 0 && onHome != null ? onHome : v -> { };
            } else if (i == 0) {
                click = v -> {
                    Intent in = new Intent(a, MainActivity.class);
                    in.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    a.startActivity(in);
                    if (!(a instanceof MainActivity)) a.finish();
                    a.overridePendingTransition(0, 0);   // no slide animation: tab switches feel instant
                };
            } else {
                click = v -> {
                    a.startActivity(new Intent(a, pages[idx]));
                    if (!(a instanceof MainActivity)) a.finish();
                    a.overridePendingTransition(0, 0);   // no slide animation: tab switches feel instant
                };
            }
            b.item(icons[i], labels[i], click);
        }
        return b.build(selected);
    }

    BottomBar item(int iconRes, String label, View.OnClickListener click) {
        items.add(new Entry(iconRes, label, click));
        return this;
    }

    private int dp(int v) { return CaseViews.dp(c, v); }

    View build(int selected) {
        FrameLayout root = new FrameLayout(c);
        root.setClipChildren(false);
        root.setClipToPadding(false);

        View bar = new View(c);
        float r = dp(20);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(AppTheme.bg(c));
        bg.setStroke(dp(1), AppTheme.cardStroke(c));   // hairline so the white bar reads against a white page
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bar.setBackground(bg);
        bar.setElevation(dp(8));
        root.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(BAR_H), Gravity.BOTTOM));

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setElevation(dp(10));
        for (int i = 0; i < items.size(); i++) {
            Entry e = items.get(i);
            View v = i == selected ? raised(e) : flat(e);
            v.setOnClickListener(e.click);
            row.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        root.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(BAR_H + TOP_EXTRA)));
        return root;
    }

    /** Unselected: dark icon and label on the white bar, both centred in the cell. */
    private View flat(Entry e) {
        LinearLayout item = new LinearLayout(c);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(0, dp(TOP_EXTRA + 10), 0, 0);
        int fg = AppTheme.navOff(c);
        item.addView(CaseViews.icon(c, e.icon, fg, 24));
        TextView t = CaseViews.text(c, e.label, 11, fg, true);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(3), 0, 0);
        // WRAP_CONTENT: LinearLayout's default for a vertical parent is MATCH_PARENT,
        // which stretched the label to the cell width and left-aligned it.
        item.addView(t, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return item;
    }

    /** Selected: circle with an accent ring, accent icon and the full label inside. */
    private View raised(Entry e) {
        FrameLayout cell = new FrameLayout(c);
        cell.setClipChildren(false);

        int accent = AppTheme.accent(c);
        LinearLayout circle = new LinearLayout(c);
        circle.setOrientation(LinearLayout.VERTICAL);
        circle.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(AppTheme.bg(c));
        g.setStroke(dp(3), accent);
        circle.setBackground(g);
        circle.setElevation(dp(4));
        circle.addView(CaseViews.icon(c, e.icon, accent, 24));
        TextView t = CaseViews.text(c, e.label, 10, accent, true);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(2), 0, 0);
        circle.addView(t, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(CIRCLE), dp(CIRCLE),
                Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        lp.topMargin = dp(2);
        cell.addView(circle, lp);
        return cell;
    }
}
