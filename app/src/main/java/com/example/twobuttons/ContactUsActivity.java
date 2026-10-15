package com.example.twobuttons;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * "We'll be back in a moment" page, opened from Settings > Contact us. Contact support is not
 * available yet, so the page says so plainly instead of pretending to connect.
 */
public class ContactUsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().hide();

        final boolean dark = AppTheme.isDark(this);
        final int ink = dark ? 0xFFE6F0E8 : 0xFF1B3D2E;
        final int sub = dark ? 0xFFA9BBAE : 0xFF5E6F63;
        final int cardFill = dark ? 0xFF18241E : 0xFFFFFFFF;
        final int cardStroke = dark ? 0xFF2A3B31 : 0xFFDDE5D6;
        final int tile = dark ? 0xFF22332A : 0xFFE8EFE1;
        final int btnFill = dark ? 0xFFD4E88F : 0xFF1B3D2E;
        final int btnText = dark ? 0xFF14201A : 0xFFFFFFFF;

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                dark ? new int[]{0xFF0E1511, 0xFF14201A} : new int[]{0xFFF7F9F2, 0xFFE9F0E2}));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(14), dp(20), dp(28));
        sv.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        // top bar: back + app name
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = CaseViews.icon(this, R.drawable.ic_arrow_back, ink, 44);
        back.setPadding(dp(10), dp(10), dp(10), dp(10));
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        top.addView(back);
        TextView app = CaseViews.text(this, getString(R.string.app_name), 15, ink, true);
        app.setPadding(dp(6), 0, 0, 0);
        top.addView(app);
        root.addView(top);

        // illustration
        MaintenanceArt art = new MaintenanceArt(this, dark);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(240), dp(240));
        alp.gravity = Gravity.CENTER_HORIZONTAL;
        alp.topMargin = dp(18);
        root.addView(art, alp);

        // badge
        LinearLayout badge = new LinearLayout(this);
        badge.setOrientation(LinearLayout.HORIZONTAL);
        badge.setGravity(Gravity.CENTER_VERTICAL);
        badge.setPadding(dp(18), dp(11), dp(20), dp(11));
        badge.setBackground(CaseViews.box(this, cardFill, 24, cardStroke, 1));
        View dot = new View(this);
        dot.setBackground(CaseViews.box(this, dark ? 0xFF8FB39B : 0xFF7FA38B, 5, 0, 0));
        badge.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        TextView bt = CaseViews.text(this, "UNDER MAINTENANCE", 12, ink, true);
        bt.setLetterSpacing(0.12f);
        bt.setPadding(dp(10), 0, 0, 0);
        badge.addView(bt);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.CENTER_HORIZONTAL;
        blp.topMargin = dp(18);
        root.addView(badge, blp);

        // heading + message
        TextView h = CaseViews.text(this, "We'll be back\nin a moment.", 32, ink, false);
        h.setTypeface(Typeface.SERIF);
        h.setGravity(Gravity.CENTER);
        h.setLineSpacing(0, 1.1f);
        root.addView(h, CaseViews.lp(this, 22, 0));

        TextView msg = CaseViews.text(this,
                "Contact and support are taking a short break while we tidy things up. "
                        + "Thank you for your patience.", 15, sub, false);
        msg.setGravity(Gravity.CENTER);
        msg.setLineSpacing(0, 1.25f);
        msg.setPadding(dp(8), 0, dp(8), 0);
        root.addView(msg, CaseViews.lp(this, 14, 0));

        // reassurance card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(CaseViews.box(this, cardFill, 20, cardStroke, 1));
        LinearLayout tileBox = new LinearLayout(this);
        tileBox.setGravity(Gravity.CENTER);
        tileBox.setBackground(CaseViews.box(this, tile, 14, 0, 0));
        tileBox.addView(CaseViews.icon(this, R.drawable.ic_lock, ink, 24));
        card.addView(tileBox, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(14), 0, 0, 0);
        words.addView(CaseViews.text(this, "Your data is safe", 15, ink, true));
        TextView ws = CaseViews.text(this,
                "Cases, clients and files stay on this device and are not affected.", 13, sub, false);
        ws.setPadding(0, dp(3), 0, 0);
        words.addView(ws);
        card.addView(words, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(card, CaseViews.lp(this, 26, 0));

        // check again
        LinearLayout btn = new LinearLayout(this);
        btn.setOrientation(LinearLayout.HORIZONTAL);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(CaseViews.box(this, btnFill, 30, 0, 0));
        ImageView spin = CaseViews.icon(this, R.drawable.ic_refresh, btnText, 22);
        btn.addView(spin);
        TextView bl = CaseViews.text(this, "Check again", 16, btnText, true);
        bl.setPadding(dp(10), 0, 0, 0);
        btn.addView(bl);
        btn.setOnClickListener(v -> {
            spin.animate().rotationBy(360f).setDuration(600).start();
            Toast.makeText(this, "Contact support is still unavailable. Please try again later.",
                    Toast.LENGTH_LONG).show();
        });
        root.addView(btn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));
        ((LinearLayout.LayoutParams) btn.getLayoutParams()).topMargin = dp(18);

        setContentView(sv);
    }

    private int dp(int v) { return CaseViews.dp(this, v); }

    /** Original illustration: a case folder with a paper, a clock and a gear badge. */
    private static final class MaintenanceArt extends View {
        private final boolean dark;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        MaintenanceArt(Context c, boolean dark) {
            super(c);
            this.dark = dark;
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight()) / 240f;
            c.scale(s, s);

            int green = dark ? 0xFF2F5D45 : 0xFF1B3D2E;
            int greenFront = dark ? 0xFF3C7457 : 0xFF2D5A43;
            int lime = 0xFFD4E88F;
            int sage = dark ? 0xFF3C5446 : 0xFFC8D5C0;
            int paper = dark ? 0xFFEAF1E6 : 0xFFFFFFFF;

            // soft disc and dotted orbit
            p.setStyle(Paint.Style.FILL);
            p.setColor(dark ? 0x14FFFFFF : 0x80FFFFFF);
            c.drawCircle(120, 120, 104, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.5f);
            p.setColor(sage);
            p.setPathEffect(new DashPathEffect(new float[]{2f, 6f}, 0));
            c.drawCircle(120, 120, 114, p);
            p.setPathEffect(null);
            p.setStyle(Paint.Style.FILL);
            p.setColor(sage);
            c.drawCircle(18, 150, 5, p);
            p.setColor(green);
            c.drawCircle(214, 150, 4, p);

            // folder back + tab
            p.setColor(green);
            c.drawRoundRect(new RectF(56, 74, 184, 196), 16, 16, p);
            c.drawRoundRect(new RectF(56, 60, 114, 92), 10, 10, p);

            // paper sheet with lines
            p.setColor(paper);
            c.drawRoundRect(new RectF(72, 50, 168, 170), 8, 8, p);
            p.setColor(sage);
            for (int i = 0; i < 3; i++) {
                float y = 72 + i * 16;
                c.drawRoundRect(new RectF(86, y, 86 + (i == 0 ? 50 : 70), y + 5), 2.5f, 2.5f, p);
            }

            // folder front
            p.setColor(greenFront);
            c.drawRoundRect(new RectF(56, 102, 184, 196), 16, 16, p);

            // clock
            p.setColor(paper);
            c.drawCircle(186, 62, 22, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3f);
            p.setColor(sage);
            c.drawCircle(186, 62, 22, p);
            p.setColor(green);
            p.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(186, 62, 186, 49, p);
            c.drawLine(186, 62, 196, 68, p);
            p.setStrokeCap(Paint.Cap.BUTT);

            // gear badge
            p.setStyle(Paint.Style.FILL);
            p.setColor(lime);
            c.drawCircle(174, 168, 32, p);
            p.setColor(green);
            c.drawCircle(174, 168, 12.5f, p);
            for (int i = 0; i < 8; i++) {
                c.save();
                c.rotate(i * 45f, 174, 168);
                c.drawRoundRect(new RectF(171, 168 - 20, 177, 168 - 11), 2f, 2f, p);
                c.restore();
            }
            p.setColor(lime);
            c.drawCircle(174, 168, 5, p);
        }
    }
}
