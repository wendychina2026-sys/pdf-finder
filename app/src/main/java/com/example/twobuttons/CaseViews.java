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

    static GradientDrawable box(Context c, int fill, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) g.setStroke(dp(c, strokeDp), strokeColor);
        return g;
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

    /** "Title ............ View All" */
    static LinearLayout sectionHeader(Context c, String title, View.OnClickListener viewAll) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(c, 22), 0, dp(c, 10));
        TextView t = text(c, title, 18, AppTheme.text(c), true);
        TextView all = text(c, "View All", 14, AppTheme.accent(c), true);
        all.setPadding(dp(c, 8), dp(c, 4), 0, dp(c, 4));
        all.setOnClickListener(viewAll);
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

    /** Big navy card: initial, name, sub type, date, court, Re-Schedule, View Details. */
    static LinearLayout upcomingCard(Activity a, CaseItems.Item it, Runnable changed) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(a, 16), dp(a, 16), dp(a, 16), dp(a, 16));
        card.setBackground(box(a, NAVY, 14, 0, 0));

        // top: circle initial + title + subtitle
        LinearLayout top = new LinearLayout(a);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView circle = text(a, it.initial(), 20, NAVY, true);
        circle.setGravity(Gravity.CENTER);
        circle.setBackground(box(a, Color.WHITE, 24, 0, 0));
        top.addView(circle, new LinearLayout.LayoutParams(dp(a, 48), dp(a, 48)));
        LinearLayout names = new LinearLayout(a);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(a, 12), 0, 0, 0);
        names.addView(text(a, it.title(), 16, Color.WHITE, true));
        if (!it.caseType.isEmpty()) names.addView(text(a, it.caseType, 13, 0xFFD0D3F5, false));
        if (!it.subType.isEmpty()) names.addView(text(a, it.subType, 13, 0xFFD0D3F5, false));
        top.addView(names, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(top);

        // divider
        View line = new View(a);
        line.setBackgroundColor(0x55FFFFFF);
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(a, 1));
        lineLp.topMargin = dp(a, 14);
        lineLp.bottomMargin = dp(a, 14);
        card.addView(line, lineLp);

        // Date | Court
        LinearLayout info = new LinearLayout(a);
        info.setOrientation(LinearLayout.HORIZONTAL);
        info.addView(infoBlock(a, R.drawable.ic_calendar, "Date", CaseItems.pretty(it.nextDate)),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        info.addView(infoBlock(a, R.drawable.ic_court, "Court", it.court.isEmpty() ? "-" : it.court),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(info);

        // buttons
        LinearLayout btns = new LinearLayout(a);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        Button re = pill(a, "Re-Schedule", Color.TRANSPARENT, Color.WHITE, true);
        re.setOnClickListener(v -> reschedule(a, it, changed));
        Button det = pill(a, "View Details", Color.WHITE, NAVY, false);
        det.setOnClickListener(v -> openDetails(a, it.path));
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, dp(a, 44), 1f);
        l1.rightMargin = dp(a, 6);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, dp(a, 44), 1f);
        l2.leftMargin = dp(a, 6);
        btns.addView(re, l1);
        btns.addView(det, l2);
        card.addView(btns, lp(a, 16, 0));
        return card;
    }

    private static LinearLayout infoBlock(Context c, int iconRes, String label, String value) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView ic = icon(c, iconRes, Color.WHITE, 24);
        row.addView(ic);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(c, 10), 0, dp(c, 6), 0);
        col.addView(text(c, label, 12, 0xFFD0D3F5, false));
        col.addView(text(c, value, 14, Color.WHITE, true));
        row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private static Button pill(Context c, String label, int fill, int textColor, boolean outline) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(textColor);
        b.setTextSize(14);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(box(c, fill, 8, Color.WHITE, outline ? 1 : 0));
        return b;
    }

    /** Light row: title, sub type, date + court. */
    static LinearLayout caseRow(Activity a, CaseItems.Item it, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(a, 14), dp(a, 12), dp(a, 14), dp(a, 12));
        row.setBackground(box(a, AppTheme.card(a), 12, AppTheme.cardStroke(a), 1));
        row.addView(text(a, it.title(), 16, AppTheme.text(a), true));
        String sub = it.subtitle();
        if (!sub.isEmpty()) row.addView(text(a, sub, 13, AppTheme.sub(a), false));
        StringBuilder info = new StringBuilder();
        if (!it.nextDate.isEmpty()) info.append(CaseItems.pretty(it.nextDate));
        if (!it.court.isEmpty()) {
            if (info.length() > 0) info.append("  |  ");
            info.append(it.court);
        }
        if (info.length() > 0) row.addView(text(a, info.toString(), 13, AppTheme.accent(a), false));
        row.setOnClickListener(click);
        return row;
    }

    static void reschedule(Activity a, CaseItems.Item it, Runnable after) {
        Calendar min = Calendar.getInstance();
        min.add(Calendar.DAY_OF_YEAR, 1);
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

        DatePickerDialog dlg = new DatePickerDialog(a, (view, y, m, d) -> {
            DetailsStore ds = new DetailsStore(a);
            DetailsStore.Details det = ds.get(it.path);
            if (det == null) return;
            det.nextDate = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
            ds.save(it.path, det);
            after.run();
        }, init.get(Calendar.YEAR), init.get(Calendar.MONTH), init.get(Calendar.DAY_OF_MONTH));
        dlg.getDatePicker().setMinDate(min.getTimeInMillis());
        dlg.show();
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
