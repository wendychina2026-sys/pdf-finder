package com.example.twobuttons;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Launch screen: blank white, logo grows (1.5 s), stays 1 s, then line + app name come in,
 * stays 1 s, then fades into MainActivity. Tap skips. Always white.
 */
public class SplashActivity extends Activity {

    private AnimatorSet anim;
    private boolean done;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().hide();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.justice_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_XY);   // stretch to box: taller statue
        col.addView(logo, new LinearLayout.LayoutParams(
                CaseViews.dp(this, 300), CaseViews.dp(this, 440)));

        View line = new View(this);
        line.setBackgroundColor(0xFF0B0B8E);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(CaseViews.dp(this, 120), CaseViews.dp(this, 3));
        ll.topMargin = CaseViews.dp(this, 14);
        col.addView(line, ll);

        TextView name = new TextView(this);
        name.setText(R.string.app_name);
        name.setTextColor(0xFF111111);
        name.setTextSize(17);
        name.setGravity(Gravity.CENTER);
        name.setLetterSpacing(0.04f);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nl.topMargin = CaseViews.dp(this, 14);
        col.addView(name, nl);

        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(col, cl);
        setContentView(root);

        // start states: logo small and invisible, name + line hidden
        logo.setAlpha(0f);
        logo.setScaleX(0.45f);
        logo.setScaleY(0.45f);
        line.setPivotX(CaseViews.dp(this, 60));
        line.setScaleX(0f);
        name.setAlpha(0f);
        name.setTranslationY(CaseViews.dp(this, 10));

        // 1. logo grows slowly (3 s), fades in during the first second, no overshoot
        ObjectAnimator g1 = ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.45f, 1f).setDuration(1500);
        ObjectAnimator g2 = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.45f, 1f).setDuration(1500);
        ObjectAnimator g3 = ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f).setDuration(500);
        g1.setInterpolator(new LinearInterpolator());
        g2.setInterpolator(new LinearInterpolator());
        AnimatorSet grow = new AnimatorSet();
        grow.playTogether(g1, g2, g3);

        // 2. logo stays 2 s, then 3. name + line come in
        ObjectAnimator l1 = ObjectAnimator.ofFloat(line, View.SCALE_X, 0f, 1f).setDuration(400);
        l1.setInterpolator(new DecelerateInterpolator());
        ObjectAnimator n1 = ObjectAnimator.ofFloat(name, View.ALPHA, 0f, 1f).setDuration(400);
        ObjectAnimator n2 = ObjectAnimator.ofFloat(name, View.TRANSLATION_Y, CaseViews.dp(this, 10), 0f).setDuration(400);
        AnimatorSet nameIn = new AnimatorSet();
        nameIn.playTogether(l1, n1, n2);
        nameIn.setStartDelay(1000);

        // 4. everything stays 2 s, then fade out into the app
        ObjectAnimator out = ObjectAnimator.ofFloat(root, View.ALPHA, 1f, 0f).setDuration(300);
        out.setStartDelay(1000);
        out.setInterpolator(new AccelerateInterpolator());

        anim = new AnimatorSet();
        anim.playSequentially(grow, nameIn, out);
        anim.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(Animator a) { if (!cancelled) goMain(); }
        });
        anim.start();

        root.setOnClickListener(v -> goMain());     // tap skips
    }

    private void goMain() {
        if (done) return;
        done = true;
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (anim != null) anim.cancel();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
