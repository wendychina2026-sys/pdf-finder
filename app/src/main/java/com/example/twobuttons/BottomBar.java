package com.example.twobuttons;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Black bottom bar. The selected item lifts out of the bar as a white circle
 * with its icon and label inside; the others sit flat on the bar.
 *
 * <pre>
 *  View bar = new BottomBar(this)
 *          .item(R.drawable.ic_home, "Home", v -> ...)
 *          .item(R.drawable.ic_pdf, "Find PDF", v -> ...)
 *          .build(0);          // 0 = index of the selected item
 * </pre>
 */
final class BottomBar {

    private static final int BAR_H = 60;      // dp, the black bar itself
    private static final int CIRCLE = 76;     // dp, diameter of the raised circle
    private static final int TOP_EXTRA = 24;  // dp, how far the circle rises above the bar
    private static final int BLACK = 0xFF000000;
    private static final int RING = 0xFF2B2B2B;

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
        bg.setColor(BLACK);
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

    /** Unselected: white icon and label on the black bar. */
    private View flat(Entry e) {
        LinearLayout item = new LinearLayout(c);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(0, dp(TOP_EXTRA + 9), 0, 0);
        item.addView(CaseViews.icon(c, e.icon, 0xFFFFFFFF, 24));
        TextView t = CaseViews.text(c, e.label, 11, 0xFFFFFFFF, true);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(0, dp(3), 0, 0);
        item.addView(t);
        return item;
    }

    /** Selected: white circle with a dark ring, black icon and label inside. */
    private View raised(Entry e) {
        FrameLayout cell = new FrameLayout(c);
        cell.setClipChildren(false);

        LinearLayout circle = new LinearLayout(c);
        circle.setOrientation(LinearLayout.VERTICAL);
        circle.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0xFFFFFFFF);
        g.setStroke(dp(5), RING);
        circle.setBackground(g);
        circle.setElevation(dp(4));
        circle.addView(CaseViews.icon(c, e.icon, BLACK, 26));
        TextView t = CaseViews.text(c, e.label, 10, BLACK, true);
        t.setSingleLine(true);
        t.setPadding(0, dp(2), 0, 0);
        circle.addView(t);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(CIRCLE), dp(CIRCLE),
                Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        lp.topMargin = dp(2);
        cell.addView(circle, lp);
        return cell;
    }
}
