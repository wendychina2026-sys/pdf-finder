package com.example.twobuttons;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Update card (download bar test build): yellow cloud header with a flying rocket, "ABOUT UPDATE" + version, numbered notes,
 * red "Update" pill. The rocket lifts off on open, then keeps hovering with a flickering flame.
 */
final class UpdateDialog {

    private UpdateDialog() { }

    /** onUpdate runs when the pill is tapped; call finish() on the returned controller when done. */
    static final class Ctl {
        final Dialog dialog;
        final Pill button;
        final Header header;
        Ctl(Dialog d, Pill b, Header h) { dialog = d; button = b; header = h; }
        void busy(String text) { button.setText(text); button.setEnabled(false); button.setProgress(0f); }
        /** frac 0..1, or -1 when the size is unknown. */
        void progress(float frac) {
            if (frac < 0) { button.setText("Downloading..."); return; }
            button.setProgress(frac);
            button.setText("Downloading " + Math.round(frac * 100) + "%");
        }
        /** Download done: pill full, rocket blasts off, then after is run. */
        void launch(Runnable after) {
            button.setProgress(1f);
            button.setText("Launching...");
            header.launch(after);
        }
        void close() { try { dialog.dismiss(); } catch (Exception ignored) { } }
    }

    /** The red pill. Doubles as the download progress bar. */
    static final class Pill extends TextView {
        private final Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();
        private float prog = 1f;
        Pill(Context c) { super(c); }
        void setProgress(float f) { prog = Math.max(0f, Math.min(1f, f)); invalidate(); }
        @Override protected void onDraw(Canvas cv) {
            if (prog < 1f) {
                float w = getWidth(), h = getHeight();
                clip.reset();
                clip.addRoundRect(new RectF(0, 0, w, h), h / 2f, h / 2f, Path.Direction.CW);
                cv.save();
                cv.clipPath(clip);
                fp.setColor(0xFFF95A5A);
                cv.drawRect(0, 0, w * prog, h, fp);
                cv.restore();
            }
            super.onDraw(cv);
        }
    }

    static Ctl show(Activity a, String version, String notes, final Runnable onUpdate) {
        return show(a, version, notes, "ABOUT UPDATE", "Update", onUpdate);
    }

    static Ctl show(Activity a, String version, String notes, String heading, String buttonText, final Runnable onUpdate) {
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(CaseViews.box(a, Color.WHITE, 24, Color.TRANSPARENT, 0));
        card.setClipToOutline(true);
        int pad = CaseViews.dp(a, 24);

        final Header header = new Header(a);
        card.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, CaseViews.dp(a, 150)));

        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(pad, CaseViews.dp(a, 18), pad, CaseViews.dp(a, 6));
        TextView about = CaseViews.text(a, heading, 15, 0xFF1B1B1F, true);
        head.addView(about, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(CaseViews.text(a, version, 15, 0xFFF0454A, true));
        card.addView(head);

        List<String> lines = noteLines(notes);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            String num = (i + 1) + ". ";
            int s = sb.length();
            sb.append(num);
            sb.setSpan(new StyleSpan(Typeface.BOLD), s, s + num.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(0xFF8A8A99), s, s + num.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append(lines.get(i));
        }
        TextView body = CaseViews.text(a, "", 15, 0xFF8A8A99, false);
        body.setText(sb);
        body.setLineSpacing(CaseViews.dp(a, 4), 1f);
        body.setPadding(pad, CaseViews.dp(a, 4), pad, CaseViews.dp(a, 18));
        card.addView(body);

        LinearLayout foot = new LinearLayout(a);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setGravity(Gravity.CENTER_HORIZONTAL);
        foot.setPadding(pad, CaseViews.dp(a, 6), pad, CaseViews.dp(a, 18));
        final Pill btn = new Pill(a);
        btn.setText(buttonText);
        btn.setTextSize(18);
        btn.setTextColor(Color.WHITE);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(CaseViews.box(a, 0xFFF95A5A, 30, Color.TRANSPARENT, 0));
        btn.setTag(null);
        btn.setElevation(CaseViews.dp(a, 8));
        if (android.os.Build.VERSION.SDK_INT >= 28) btn.setOutlineAmbientShadowColor(0xFFF95A5A);
        if (android.os.Build.VERSION.SDK_INT >= 28) btn.setOutlineSpotShadowColor(0xFFF95A5A);
        btn.setOnClickListener(v -> onUpdate.run());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(CaseViews.dp(a, 150), CaseViews.dp(a, 48));
        bp.topMargin = CaseViews.dp(a, 6);
        foot.addView(btn, bp);
        TextView later = CaseViews.text(a, "Later", 14, 0xFF9A9AA8, false);
        later.setPadding(CaseViews.dp(a, 20), CaseViews.dp(a, 12), CaseViews.dp(a, 20), 0);
        later.setOnClickListener(v -> d.dismiss());
        foot.addView(later);
        card.addView(foot);

        d.setContentView(card);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(0.55f);
            int sw = a.getResources().getDisplayMetrics().widthPixels;
            w.setLayout(Math.min(sw - CaseViews.dp(a, 48), CaseViews.dp(a, 340)), WindowManager.LayoutParams.WRAP_CONTENT);
        }
        d.show();
        return new Ctl(d, btn, header);
    }

    private static List<String> noteLines(String notes) {
        List<String> out = new ArrayList<>();
        if (notes != null) {
            for (String raw : notes.split("\\r?\\n")) {
                String t = raw.replaceAll("^[\\s#>*\\-\\u2022\\d.)]+", "").replace("**", "").trim();
                if (t.isEmpty()) continue;
                if (t.length() > 90) t = t.substring(0, 87) + "...";
                out.add(t);
                if (out.size() >= 5) break;
            }
        }
        if (out.isEmpty()) out.add("Bug fixes and improvements");
        return out;
    }

    /** Yellow header: drifting clouds, shooting stars, flying rocket. */
    static final class Header extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final long t0 = System.nanoTime();
        private boolean running;
        private long launchAt = -1;
        private Runnable afterLaunch;

        void launch(Runnable after) {
            afterLaunch = after;
            launchAt = System.nanoTime();
            postInvalidateOnAnimation();
        }

        Header(Context c) { super(c); }

        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); running = true; postInvalidateOnAnimation(); }
        @Override protected void onDetachedFromWindow() { running = false; super.onDetachedFromWindow(); }

        @Override protected void onDraw(Canvas cv) {
            float W = getWidth(), H = getHeight();
            float dp = getResources().getDisplayMetrics().density;
            float t = (System.nanoTime() - t0) / 1e9f;

            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFD54A);
            cv.drawRect(0, 0, W, H, p);

            // soft clouds drifting sideways
            p.setColor(0xFFFFDE6E);
            float[][] cl = {{0.12f, 0.62f, 34}, {0.30f, 0.50f, 44}, {0.52f, 0.60f, 38}, {0.72f, 0.48f, 46}, {0.92f, 0.62f, 36}};
            for (int i = 0; i < cl.length; i++) {
                float dx = (float) Math.sin(t * 0.6f + i) * 6 * dp;
                cv.drawCircle(W * cl[i][0] + dx, H * cl[i][1], cl[i][2] * dp, p);
            }

            // shooting stars
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(1.6f * dp);
            for (int i = 0; i < 3; i++) {
                float ph = ((t * 0.45f) + i / 3f) % 1f;
                float x = W * (0.95f - ph * 1.1f) + i * 14 * dp;
                float y = H * (-0.1f + ph * 1.1f) + i * 10 * dp;
                int al = (int) (200 * Math.sin(Math.PI * ph));
                p.setColor(Color.argb(Math.max(al, 0), 255, 255, 255));
                cv.drawLine(x, y, x + 26 * dp, y - 26 * dp, p);
            }
            p.setStyle(Paint.Style.FILL);

            // rocket: lifts off from the cloud line, then hovers
            float enter = Math.min(1f, t / 1.0f);
            float ease = 1f - (1f - enter) * (1f - enter) * (1f - enter);
            float hover = (float) Math.sin(t * 2.6f) * 4 * dp * enter;
            float cx = W / 2f + (float) Math.sin(t * 1.3f) * 2 * dp;
            float L = 0f;
            if (launchAt >= 0) {
                L = Math.min(1f, (System.nanoTime() - launchAt) / 1e9f / 0.9f);
                if (L >= 1f && afterLaunch != null) {
                    final Runnable r = afterLaunch;
                    afterLaunch = null;
                    post(r);
                }
            }
            float cy = H * 0.42f + (1f - ease) * 70 * dp + hover * (1f - L) - L * L * H * 1.5f;
            float rw = 13 * dp, rh = 30 * dp;

            // flame
            float fl = (0.75f + 0.25f * (float) Math.sin(t * 28f)) * (1f + 2.2f * L);
            path.reset();
            path.moveTo(cx - rw * 0.45f, cy + rh * 0.55f);
            path.quadTo(cx, cy + rh * (0.55f + 0.95f * fl), cx + rw * 0.45f, cy + rh * 0.55f);
            path.close();
            p.setColor(0xFFFF9A3C);
            cv.drawPath(path, p);
            path.reset();
            path.moveTo(cx - rw * 0.22f, cy + rh * 0.55f);
            path.quadTo(cx, cy + rh * (0.55f + 0.55f * fl), cx + rw * 0.22f, cy + rh * 0.55f);
            path.close();
            p.setColor(0xFFFFF2A8);
            cv.drawPath(path, p);

            // exhaust sparks falling away
            for (int i = 0; i < 5; i++) {
                float ph = ((t * 1.4f) + i * 0.2f) % 1f;
                p.setColor(Color.argb((int) (190 * (1 - ph)), 255, 255, 255));
                cv.drawCircle(cx + (i - 2) * 4 * dp, cy + rh * 0.9f + ph * 40 * dp, (2.6f - ph * 1.6f) * dp, p);
            }

            // fins
            p.setColor(0xFFE4E7F2);
            path.reset();
            path.moveTo(cx - rw * 0.8f, cy + rh * 0.05f);
            path.lineTo(cx - rw * 1.45f, cy + rh * 0.58f);
            path.lineTo(cx - rw * 0.6f, cy + rh * 0.45f);
            path.close();
            cv.drawPath(path, p);
            path.reset();
            path.moveTo(cx + rw * 0.8f, cy + rh * 0.05f);
            path.lineTo(cx + rw * 1.45f, cy + rh * 0.58f);
            path.lineTo(cx + rw * 0.6f, cy + rh * 0.45f);
            path.close();
            cv.drawPath(path, p);

            // body
            p.setColor(Color.WHITE);
            path.reset();
            path.moveTo(cx, cy - rh * 0.62f);
            path.cubicTo(cx + rw * 1.1f, cy - rh * 0.25f, cx + rw * 0.95f, cy + rh * 0.3f, cx + rw * 0.6f, cy + rh * 0.58f);
            path.lineTo(cx - rw * 0.6f, cy + rh * 0.58f);
            path.cubicTo(cx - rw * 0.95f, cy + rh * 0.3f, cx - rw * 1.1f, cy - rh * 0.25f, cx, cy - rh * 0.62f);
            path.close();
            cv.drawPath(path, p);
            p.setColor(0xFFF0F2FA);
            cv.drawRect(new RectF(cx + rw * 0.25f, cy - rh * 0.2f, cx + rw * 0.7f, cy + rh * 0.55f), p);
            p.setColor(0xFFFFFFFF);
            path.reset();
            path.moveTo(cx, cy - rh * 0.62f);
            path.cubicTo(cx + rw * 1.1f, cy - rh * 0.25f, cx + rw * 0.95f, cy + rh * 0.3f, cx + rw * 0.6f, cy + rh * 0.58f);
            path.lineTo(cx + rw * 0.25f, cy + rh * 0.58f);
            path.close();
            // window
            p.setColor(0xFFDDE3FF);
            cv.drawCircle(cx, cy - rh * 0.08f, rw * 0.48f, p);
            p.setColor(0xFF5B7BFF);
            cv.drawCircle(cx, cy - rh * 0.08f, rw * 0.34f, p);
            p.setColor(0xFF9DB1FF);
            cv.drawCircle(cx - rw * 0.08f, cy - rh * 0.13f, rw * 0.12f, p);

            // wavy yellow edge at the bottom of the header
            p.setColor(0xFFFFCB3B);
            path.reset();
            path.moveTo(0, H);
            path.lineTo(0, H * 0.84f);
            path.cubicTo(W * 0.25f, H * 0.72f, W * 0.45f, H * 0.98f, W * 0.7f, H * 0.82f);
            path.cubicTo(W * 0.85f, H * 0.74f, W * 0.95f, H * 0.82f, W, H * 0.80f);
            path.lineTo(W, H);
            path.close();
            cv.drawPath(path, p);
            p.setColor(Color.WHITE);
            path.reset();
            path.moveTo(0, H);
            path.lineTo(0, H * 0.92f);
            path.cubicTo(W * 0.3f, H * 0.84f, W * 0.55f, H * 1.0f, W, H * 0.9f);
            path.lineTo(W, H);
            path.close();
            cv.drawPath(path, p);

            if (running) postInvalidateOnAnimation();
        }
    }
}
