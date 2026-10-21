package com.example.twobuttons;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
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
        card.setBackground(CaseViews.box(a, AppTheme.bg(a), 24, AppTheme.cardStroke(a), 1));
        card.setClipToOutline(true);
        card.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        card.setElevation(dp(2));

        // ---- header band ----
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(22), dp(20), dp(22), dp(20));
        GradientDrawable band = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{AppTheme.card(a), AppTheme.bg(a)});
        head.setBackground(band);

        if (fixed) {
            View bar = new View(a);
            bar.setBackground(CaseViews.box(a, accent, 2, 0, 0));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(4), dp(26));
            blp.rightMargin = dp(12);
            blp.gravity = Gravity.TOP;
            head.addView(bar, blp);
        }

        LinearLayout left = new LinearLayout(a);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(CaseViews.text(a, title, 20, AppTheme.text(a), true));
        TextView sub = CaseViews.text(a, subtitle, 14, AppTheme.sub(a), false);
        sub.setPadding(0, dp(8), 0, 0);
        left.addView(sub);
        head.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final ImageView chevron;
        if (fixed) {
            LinearLayout right = new LinearLayout(a);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.CENTER_HORIZONTAL);
            right.addView(CaseViews.text(a, String.valueOf(count), 30, AppTheme.text(a), true));
            right.addView(CaseViews.text(a, countLabel, 14, AppTheme.sub(a), false));
            head.addView(right);
            chevron = null;
        } else {
            TextView pill = CaseViews.text(a, String.valueOf(count), 18, AppTheme.text(a), true);
            pill.setGravity(Gravity.CENTER);
            pill.setBackground(CaseViews.box(a, AppTheme.cardStroke(a), 14, 0, 0));
            head.addView(pill, new LinearLayout.LayoutParams(dp(40), dp(40)));
            chevron = CaseViews.icon(a, R.drawable.ic_expand_more, AppTheme.text(a), 24);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(24), dp(24));
            clp.leftMargin = dp(14);
            head.addView(chevron, clp);
        }
        card.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- body ----
        final LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        if (rows.isEmpty()) {
            body.addView(emptyState());
        } else {
            LinearLayout list = new LinearLayout(a);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(dp(14), dp(14), dp(14), dp(6));
            for (View r : rows) list.addView(r, CaseViews.lp(a, 0, 10));
            body.addView(list);
            if (viewAll != null) {
                TextView all = CaseViews.text(a, "View All", 14, accent, true);
                all.setGravity(Gravity.CENTER);
                all.setPadding(dp(16), dp(10), dp(16), dp(16));
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
        box.setPadding(dp(20), dp(34), dp(20), dp(34));

        LinearLayout tile = new LinearLayout(a);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(CaseViews.box(a, AppTheme.cardStroke(a), 20, 0, 0));
        tile.addView(CaseViews.icon(a, R.drawable.ic_calendar, AppTheme.accent(a), 26));
        box.addView(tile, new LinearLayout.LayoutParams(dp(56), dp(56)));

        TextView t = CaseViews.text(a, emptyTitle, 18, AppTheme.text(a), true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(20), 0, 0);
        box.addView(t);
        TextView s = CaseViews.text(a, emptySub, 14, AppTheme.sub(a), false);
        s.setGravity(Gravity.CENTER);
        s.setPadding(0, dp(10), 0, 0);
        box.addView(s);
        return box;
    }
}
