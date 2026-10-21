package com.example.twobuttons;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Home page hearing card: rounded bordered card, tinted header band, white body.
 *
 * <ul>
 *   <li>Fixed (Today): accent bar + title/date on the left, big count + label on the right,
 *       body always open.</li>
 *   <li>Collapsible (Tomorrow, Upcoming): title/date on the left, count pill + chevron on the
 *       right, tap the header to open or close the body. The open state lives in the boolean[]
 *       the caller passes, so it survives the page redrawing.</li>
 * </ul>
 */
final class SectionCard {

    private final Activity a;
    private String title = "", subtitle = "", countLabel = "Hearings";
    private String emptyTitle = "No hearings scheduled", emptySub = "Your agenda is clear for this date.";
    private int count = 0;
    private boolean[] openState;                 // null = fixed (always open)
    private View.OnClickListener viewAll;
    private final List<View> rows = new ArrayList<>();
    private View belowHeader;                    // e.g. the search bar, shown under the title
    SectionCard belowHeader(View v) { belowHeader = v; return this; }

    SectionCard(Activity a) { this.a = a; }

    SectionCard title(String t) { title = t; return this; }
    SectionCard subtitle(String s) { subtitle = s; return this; }
    SectionCard count(int n, String label) { count = n; countLabel = label; return this; }
    SectionCard empty(String t, String s) { emptyTitle = t; emptySub = s; return this; }
    SectionCard collapsible(boolean[] state) { openState = state; return this; }
    SectionCard viewAll(View.OnClickListener l) { viewAll = l; return this; }
    SectionCard row(View v) { rows.add(v); return this; }

    private int dp(int v) { return CaseViews.dp(a, v); }

    View build() {
        final boolean fixed = openState == null;
        final int accent = AppTheme.accent(a);

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(CaseViews.box(a, AppTheme.bg(a), 16, AppTheme.cardStroke(a), 1));
        card.setElevation(dp(1));
        card.setPadding(dp(1), dp(1), dp(1), dp(1));   // children stay inside the border stroke

        // ---- header band ----
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable band = new GradientDrawable();
        band.setColor(AppTheme.card(a));
        float rad = dp(15);
        band.setCornerRadii(new float[]{rad, rad, rad, rad, 0, 0, 0, 0});
        head.setBackground(band);

        if (fixed) {
            View bar = new View(a);
            bar.setBackground(CaseViews.box(a, accent, 2, 0, 0));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(3), dp(18));
            blp.rightMargin = dp(8);
            blp.gravity = Gravity.TOP;
            head.addView(bar, blp);
        }

        LinearLayout left = new LinearLayout(a);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(CaseViews.text(a, title, 15, AppTheme.text(a), true));
        TextView sub = CaseViews.text(a, subtitle, 12, AppTheme.sub(a), false);
        sub.setPadding(0, dp(2), 0, 0);
        left.addView(sub);
        head.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final ImageView chevron;
        if (fixed) {
            LinearLayout right = new LinearLayout(a);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView num = CaseViews.text(a, String.valueOf(count), 20, AppTheme.text(a), true);
            num.setGravity(Gravity.CENTER);
            TextView lab = CaseViews.text(a, countLabel, 11, AppTheme.sub(a), false);
            lab.setGravity(Gravity.CENTER);
            right.addView(num, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            right.addView(lab, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.leftMargin = dp(10);
            rlp.rightMargin = dp(4);      // keep clear of the card border
            head.addView(right, rlp);
            chevron = null;
        } else {
            TextView pill = CaseViews.text(a, String.valueOf(count), 13, AppTheme.text(a), true);
            pill.setGravity(Gravity.CENTER);
            pill.setBackground(CaseViews.box(a, AppTheme.cardStroke(a), 10, 0, 0));
            head.addView(pill, new LinearLayout.LayoutParams(dp(30), dp(30)));
            chevron = CaseViews.icon(a, R.drawable.ic_expand_more, AppTheme.text(a), 20);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(20), dp(20));
            clp.leftMargin = dp(8);
            clp.rightMargin = dp(2);
            head.addView(chevron, clp);
        }
        card.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (belowHeader != null) {
            LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp2.setMargins(dp(12), dp(8), dp(12), dp(4));
            card.addView(belowHeader, blp2);
        }

        // ---- body ----
        final LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        if (rows.isEmpty()) {
            body.addView(emptyState());
        } else {
            LinearLayout list = new LinearLayout(a);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(dp(10), dp(8), dp(10), dp(2));
            for (View r : rows) list.addView(r, CaseViews.lp(a, 0, 8));
            body.addView(list);
            if (viewAll != null) {
                TextView all = CaseViews.text(a, "View All", 12, accent, true);
                all.setGravity(Gravity.CENTER);
                all.setPadding(dp(16), dp(6), dp(16), dp(10));
                all.setOnClickListener(viewAll);
                body.addView(all, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
        card.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!fixed) {
            Runnable apply = () -> {
                body.setVisibility(openState[0] ? View.VISIBLE : View.GONE);
                chevron.setRotation(openState[0] ? 180f : 0f);
            };
            apply.run();
            head.setOnClickListener(v -> { openState[0] = !openState[0]; apply.run(); });
        }
        return card;
    }

    private View emptyState() {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(16), dp(18), dp(16), dp(18));

        LinearLayout tile = new LinearLayout(a);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(CaseViews.box(a, AppTheme.cardStroke(a), 14, 0, 0));
        tile.addView(CaseViews.icon(a, R.drawable.ic_calendar, AppTheme.accent(a), 20));
        box.addView(tile, new LinearLayout.LayoutParams(dp(38), dp(38)));

        TextView t = CaseViews.text(a, emptyTitle, 14, AppTheme.text(a), true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(10), 0, 0);
        box.addView(t);
        TextView s = CaseViews.text(a, emptySub, 12, AppTheme.sub(a), false);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, dp(4), 0, 0);
        box.addView(s);
        return box;
    }
}
