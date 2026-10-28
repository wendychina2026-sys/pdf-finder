package com.example.twobuttons;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * First screen of the app: plays res/raw/intro.mp4 full screen (cropped to fill, no bars),
 * then opens SplashActivity. Tap skips. Any video error goes straight to the splash.
 */
public class IntroActivity extends Activity implements TextureView.SurfaceTextureListener {

    private MediaPlayer player;
    private TextureView tv;
    private boolean done;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) getActionBar().hide();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        tv = new TextureView(this);
        tv.setSurfaceTextureListener(this);
        root.addView(tv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.setOnClickListener(v -> goNext());     // tap skips
        setContentView(root);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        try {
            player = new MediaPlayer();
            AssetFileDescriptor fd = getResources().openRawResourceFd(R.raw.intro);
            player.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            fd.close();
            player.setSurface(new Surface(st));
            player.setVolume(0f, 0f);
            player.setOnPreparedListener(mp -> {
                cover(w, h, mp.getVideoWidth(), mp.getVideoHeight());
                mp.start();
            });
            player.setOnCompletionListener(mp -> goNext());
            player.setOnErrorListener((mp, what, extra) -> { goNext(); return true; });
            player.prepareAsync();
        } catch (Exception e) {
            goNext();
        }
    }

    /** Scale the video so it fills the whole screen; the overflow is cropped (like centerCrop). */
    private void cover(int vw, int vh, int w, int h) {
        if (w <= 0 || h <= 0) return;
        float s = Math.max((float) vw / w, (float) vh / h);
        Matrix m = new Matrix();
        m.setScale((w * s) / vw, (h * s) / vh, vw / 2f, vh / 2f);
        tv.setTransform(m);
    }

    private void goNext() {
        if (done) return;
        done = true;
        startActivity(new Intent(this, SplashActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st) { }

    @Override
    protected void onDestroy() {
        if (player != null) {
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
