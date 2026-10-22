package com.example.twobuttons;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Shared look for the main page and case lists. Navy cards, rounded boxes. */
class CaseViews {

    static final int NAVY = 0xFF0B0B8E;
    static final int GREY = 0xFF6B6F80;

    static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Spinner adapter whose rows wrap to as many lines as needed: long names are never cut off. */
    static android.widget.ArrayAdapter<String> wrapAdapter(Context c, String[] items) {
        return new android.widget.ArrayAdapter<String>(c, android.R.layout.simple_spinner_dropdown_item, items) {
            private View fix(View v) {
                if (v instanceof TextView) {
                    TextView t = (TextView) v;
                    t.setSingleLine(false);
                    t.setMaxLines(4);
                    t.setEllipsize(null);
                    t.setTextSize(14);
                    t.setPadding(dp(getContext(), 10), dp(getContext(), 10), dp(getContext(), 10), dp(getContext(), 10));
                }
                return v;
            }
            @Override public View getView(int p, View cv, android.view.ViewGroup parent) {
                View v = fix(super.getView(p, cv, parent));
                if (v instanceof TextView) {            // closed box: one line, same height as a text box
                    TextView t = (TextView) v;
                    t.setSingleLine(true);
                    t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    t.setTextSize(15);
                    t.setPadding(dp(getContext(), 8), 0, 0, 0);
                    t.setGravity(android.view.Gravity.CENTER_VERTICAL);
                }
                return v;
            }
            @Override public View getDropDownView(int p, View cv, android.view.ViewGroup parent) {
                return fix(super.getDropDownView(p, cv, parent));
            }
        };
    }

    static GradientDrawable box(Context c, int fill, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) g.setStroke(dp(c, strokeDp), strokeColor);
        return g;
    }

    /** Fractional sp, for sizes that must match a 0.9-scaled page (e.g. 10.8). */
    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = text(c, s, 1, color, bold);
        t.setTextSize(sp);
        return t;
    }

    static TextView text(Context c, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    static ImageView icon(Context c, int res, int color, int sizeDp) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    static LinearLayout.LayoutParams lp(Context c, int topDp, int bottomDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(c, topDp);
        p.bottomMargin = dp(c, bottomDp);
        return p;
    }

    /** "Title ............ View All". Pass null viewAll to show it disabled. */
    static LinearLayout sectionHeader(Context c, String title, View.OnClickListener viewAll) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(c, 22), 0, dp(c, 10));
        TextView t = text(c, title, 18, AppTheme.text(c), true);
        TextView all = text(c, "View All", 14, AppTheme.accent(c), true);
        all.setPadding(dp(c, 8), dp(c, 4), 0, dp(c, 4));
        if (viewAll != null) {
            all.setOnClickListener(viewAll);
        } else {
            all.setEnabled(false);
            all.setClickable(false);
            all.setAlpha(0.35f);   // greyed out: nothing to view
        }
        row.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(all);
        return row;
    }

    static TextView emptyNote(Context c, String s) {
        TextView t = text(c, s, 14, AppTheme.sub(c), false);
        t.setPadding(dp(c, 16), dp(c, 18), dp(c, 16), dp(c, 18));
        t.setBackground(box(c, AppTheme.card(c), 12, AppTheme.cardStroke(c), 1));
        return t;
    }

    /** Upcoming case card: avatar, name, chips, next date and Re-Schedule / View Details. */
    static View upcomingCard(Activity a, CaseItems.Item it, Runnable changed) {
        return upcomingCard(a, it, changed, false);
    }

    static View upcomingCard(Activity a, CaseItems.Item it, Runnable changed, boolean compact) {
        AppCard card = compact ? new AppCard(a).compact() : new AppCard(a);
        return card
                .avatarText(it.initial())
                .title(it.title())
                .subtitle(it.subtitle())
                .line(it.court)
                .chip("Upcoming", AppCard.Tone.ACCENT)
                .footer(CaseItems.pretty(it.nextDate), "Next hearing")
                .secondary("Re-Schedule", v -> reschedule(a, it, changed))
                .primary("View Details", v -> openDetails(a, it.path))
                .onClick(v -> openDetails(a, it.path))
                .build();
    }

    /** Standard case card used in every list. */
    static View caseRow(Activity a, CaseItems.Item it, View.OnClickListener click) {
        return caseRow(a, it, click, false);
    }

    static View caseRow(Activity a, CaseItems.Item it, View.OnClickListener click, boolean compact) {
        AppCard card = compact ? new AppCard(a).compact() : new AppCard(a);
        return card
                .avatarText(it.initial())
                .title(it.title())
                .subtitle(it.subtitle())
                .line(it.court)
                .footer(CaseItems.pretty(it.nextDate), it.nextDate.isEmpty() ? "" : "Next hearing")
                .primary("Details", click)
                .onClick(click)
                .build();
    }

    static void reschedule(Activity a, CaseItems.Item it, Runnable after) {
        Calendar min = Calendar.getInstance();   // today is allowed
        min.set(Calendar.HOUR_OF_DAY, 0);
        min.set(Calendar.MINUTE, 0);
        min.set(Calendar.SECOND, 0);
        min.set(Calendar.MILLISECOND, 0);

        Calendar init = (Calendar) min.clone();
        if (!it.nextDate.isEmpty()) {
            try {
                Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it.nextDate);
                Calendar cur = Calendar.getInstance();
                cur.setTime(parsed);
                if (!cur.before(min)) init = cur;
            } catch (ParseException ignored) {
            }
        }

        PickerDialogs.calendar(a, "Select Next Date", init, min, x -> {
            DetailsStore ds = new DetailsStore(a);
            DetailsStore.Details det = ds.get(it.path);
            if (det == null) return;
            det.nextDate = String.format(Locale.US, "%04d-%02d-%02d", x.get(Calendar.YEAR),
                    x.get(Calendar.MONTH) + 1, x.get(Calendar.DAY_OF_MONTH));
            ds.save(it.path, det);
            after.run();
        });
    }

    static void openDetails(Activity a, String path) {
        if (path == null || path.isEmpty()) {
            Toast.makeText(a, "No file linked to this case", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(a, CaseDetailsActivity.class);
        i.putExtra("path", path);
        a.startActivity(i);
    }

    static void openPdf(Activity a, String path) {
        if (path == null || path.isEmpty()) {
            Toast.makeText(a, "No file linked to this case", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(
                    a, a.getPackageName() + ".fileprovider", new File(path));
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/pdf");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, "No PDF viewer app installed", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(a, "Cannot open: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
