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
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Launch screen: the Lady Justice logo rises and fades in, a line draws under it, the app name
 * fades in, short hold, then the whole screen fades into MainActivity (~2 s). Always white.
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
        logo.setAdjustViewBounds(true);
        col.addView(logo, new LinearLayout.LayoutParams(
                CaseViews.dp(this, 150), CaseViews.dp(this, 226)));

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

        // start states
        logo.setAlpha(0f);
        logo.setScaleX(0.82f);
        logo.setScaleY(0.82f);
        logo.setTranslationY(CaseViews.dp(this, 36));
        line.setPivotX(CaseViews.dp(this, 60));
        line.setScaleX(0f);
        name.setAlpha(0f);
        name.setTranslationY(CaseViews.dp(this, 10));

        ObjectAnimator a1 = ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f).setDuration(650);
        ObjectAnimator a2 = ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.82f, 1f).setDuration(800);
        ObjectAnimator a3 = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.82f, 1f).setDuration(800);
        a2.setInterpolator(new OvershootInterpolator(1.2f));
        a3.setInterpolator(new OvershootInterpolator(1.2f));
        ObjectAnimator a4 = ObjectAnimator.ofFloat(logo, View.TRANSLATION_Y, CaseViews.dp(this, 36), 0f).setDuration(800);
        a4.setInterpolator(new DecelerateInterpolator(2f));
        AnimatorSet logoIn = new AnimatorSet();
        logoIn.playTogether(a1, a2, a3, a4);

        ObjectAnimator l1 = ObjectAnimator.ofFloat(line, View.SCALE_X, 0f, 1f).setDuration(450);
        l1.setInterpolator(new DecelerateInterpolator());

        ObjectAnimator n1 = ObjectAnimator.ofFloat(name, View.ALPHA, 0f, 1f).setDuration(450);
        ObjectAnimator n2 = ObjectAnimator.ofFloat(name, View.TRANSLATION_Y, CaseViews.dp(this, 10), 0f).setDuration(450);
        AnimatorSet nameIn = new AnimatorSet();
        nameIn.playTogether(n1, n2);

        ObjectAnimator out = ObjectAnimator.ofFloat(root, View.ALPHA, 1f, 0f).setDuration(300);
        out.setStartDelay(450);                    // hold on the finished frame
        out.setInterpolator(new AccelerateInterpolator());

        anim = new AnimatorSet();
        anim.playSequentially(logoIn, l1, nameIn, out);
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
