package com.example.twobuttons;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.InputStream;
import java.util.List;

public class MainActivity extends BaseActivity {

    private static final int REQ_CAMERA = 21;

    private ImageView avatar;
    private EditText searchBox;
    private LinearLayout sections;
    private ScrollView scroll;
    private ImageView timeIcon;
    private String shownGif = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(AppTheme.bg(this));
        sv.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));

        LinearLayout.LayoutParams wide = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        root.addView(buildHeader(), wide);
        root.addView(buildSearch());

        sections = new LinearLayout(this);
        sections.setOrientation(LinearLayout.VERTICAL);
        root.addView(sections, wide);

        sv.addView(root);
        scroll = sv;

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(AppTheme.bg(this));
        page.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(buildNav(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(page);
    }

    // ---------- bottom nav: Home | Find PDF | Marked PDF | Advanced Search ----------

    private LinearLayout buildNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(4), dp(10), dp(4), dp(10));
        float r = dp(20);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(AppTheme.navBg(this));
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        nav.setBackground(bg);
        nav.setElevation(dp(8));

        nav.addView(navItem(R.drawable.ic_home, "Home", true,
                v -> scroll.smoothScrollTo(0, 0)), navLp());
        nav.addView(navItem(R.drawable.ic_pdf, "Find PDF", false,
                v -> startActivity(new Intent(this, FindPdfsActivity.class))), navLp());
        nav.addView(navItem(R.drawable.ic_marked, "Marked PDF", false,
                v -> startActivity(new Intent(this, MarkedFilesActivity.class))), navLp());
        nav.addView(navItem(R.drawable.ic_advsearch, "Advanced Search", false,
                v -> startActivity(new Intent(this, AdvancedSearchActivity.class))), navLp());
        nav.addView(navItem(R.drawable.ic_settings, "Settings", false,
                v -> startActivity(new Intent(this, SettingsActivity.class))), navLp());
        return nav;
    }

    private LinearLayout.LayoutParams navLp() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout navItem(int iconRes, String label, boolean active,
                                 android.view.View.OnClickListener click) {
        int color = active ? AppTheme.accent(this) : AppTheme.navOff(this);
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(0, dp(4), 0, dp(4));
        ImageView ic = CaseViews.icon(this, iconRes, color, 24);
        item.addView(ic);
        TextView t = CaseViews.text(this, label, 11, color, active);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(2);
        t.setPadding(0, dp(4), 0, 0);
        item.addView(t);
        item.setOnClickListener(click);
        return item;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // ---------- search box (rounded, magnifier icon) ----------

    private LinearLayout buildSearch() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(14), dp(2), dp(14), dp(2));
        box.setBackground(CaseViews.box(this, AppTheme.field(this), 10, AppTheme.fieldStroke(this), 1));

        box.addView(CaseViews.icon(this, R.drawable.ic_search, AppTheme.sub(this), 22));

        searchBox = new EditText(this);
        searchBox.setHint("Search today's cases by case name");
        searchBox.setSingleLine(true);
        searchBox.setTextSize(15);
        searchBox.setBackground(null);
        searchBox.setPadding(dp(12), dp(12), 0, dp(12));
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { refresh(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        box.addView(searchBox, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        box.setLayoutParams(lp);
        return box;
    }

    // ---------- content ----------

    private void refresh() {
        if (searchBox == null) return;
        showSections();
    }

    private void showSections() {
        sections.setVisibility(android.view.View.VISIBLE);
        sections.removeAllViews();

        // Upcoming Cases
        sections.addView(CaseViews.sectionHeader(this, "Upcoming Cases",
                v -> openList("upcoming")));
        List<CaseItems.Item> up = CaseItems.upcoming(this);
        if (up.isEmpty()) {
            sections.addView(CaseViews.emptyNote(this, "No upcoming cases"));
        } else {
            sections.addView(CaseViews.upcomingCard(this, up.get(0), this::refresh));
        }

        // Today's Schedule
        sections.addView(CaseViews.sectionHeader(this, "Today's Schedule",
                v -> openList("today")));
        String q = searchBox.getText().toString().trim();
        List<CaseItems.Item> today = CaseItems.todayMatching(this, q);
        if (today.isEmpty()) {
            sections.addView(CaseViews.emptyNote(this,
                    q.isEmpty() ? "Nothing scheduled today" : "No matching case today"));
        } else {
            int n = q.isEmpty() ? Math.min(3, today.size()) : today.size();
            for (int i = 0; i < n; i++) {
                CaseItems.Item it = today.get(i);
                sections.addView(CaseViews.caseRow(this, it,
                        v -> CaseViews.openDetails(this, it.path)), CaseViews.lp(this, 0, 10));
            }
            if (today.size() > n) {
                sections.addView(CaseViews.text(this, "+" + (today.size() - n) + " more",
                        13, AppTheme.sub(this), false));
            }
        }
        // Legal Diary pages
        sections.addView(plainHeader("Legal Diary"));
        sections.addView(LegalUi.navRow(this, R.drawable.ic_person, "Clients",
                "Contacts, cases and payments", ClientDetailsActivity.class), CaseViews.lp(this, 0, 10));
        sections.addView(LegalUi.navRow(this, R.drawable.ic_pdf, "Documents",
                "Petitions, orders, evidence and files", DocumentsActivity.class), CaseViews.lp(this, 0, 10));
        sections.addView(LegalUi.navRow(this, R.drawable.ic_bell, "Reminders",
                "Hearing reminders sent to clients", ReminderDetailsActivity.class), CaseViews.lp(this, 0, 10));
    }

    private TextView plainHeader(String title) {
        TextView t = CaseViews.text(this, title, 18, AppTheme.text(this), true);
        t.setPadding(0, dp(22), 0, dp(10));
        return t;
    }

    private void openList(String mode) {
        Intent i = new Intent(this, CasesListActivity.class);
        i.putExtra("mode", mode);
        startActivity(i);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private LinearLayout buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(12));

        avatar = new ImageView(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xFFE0E0E0);
        avatar.setBackground(bg);
        avatar.setClipToOutline(true);
        avatar.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        avatar.setOnClickListener(v -> onAvatarClick());
        loadAvatar();

        // right side: date on top, [time + time-of-day gif] below
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(14), 0, 0, 0);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEE, dd MMM yyyy");
        date.setFormat24Hour("EEE, dd MMM yyyy");
        date.setTextSize(14);
        date.setTypeface(null, Typeface.BOLD);
        date.setTextColor(AppTheme.sub(this));

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("hh:mm:ss a");
        clock.setFormat24Hour("hh:mm:ss a");
        clock.setTextSize(20);
        clock.setTypeface(null, Typeface.BOLD);
        clock.setTextColor(AppTheme.text(this));
        clock.setSingleLine(true);

        timeIcon = new ImageView(this);
        timeIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(44), dp(44));
        ilp.leftMargin = dp(6);
        tickIcon();

        timeRow.addView(clock);
        timeRow.addView(timeIcon, ilp);
        right.addView(date);
        right.addView(timeRow);

        row.addView(avatar, new LinearLayout.LayoutParams(dp(72), dp(72)));
        row.addView(right, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    // time-of-day animated gif (assets/time_*.gif). Checks again every 30s.
    private void tickIcon() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String file;
        if (h >= 5 && h < 10) file = "time_morning.gif";          // morning 5-10
        else if (h >= 10 && h < 17) file = "time_noon.gif";       // noon 10-5pm
        else if (h >= 17 && h < 19) file = "time_evening.gif";    // evening 5-7pm
        else if (h >= 19 || h < 2) file = "time_midnight.gif";    // midnight 7pm-2am
        else file = "time_early.gif";                             // early morning 2-5am
        if (!file.equals(shownGif)) {
            shownGif = file;
            showGif(timeIcon, file);
        }
        timeIcon.postDelayed(() -> {
            if (timeIcon.isAttachedToWindow()) tickIcon();
        }, 30000);
    }

    private void showGif(ImageView iv, String file) {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.Source src = ImageDecoder.createSource(getAssets(), file);
                Drawable d = ImageDecoder.decodeDrawable(src);
                iv.setImageDrawable(d);
                if (d instanceof AnimatedImageDrawable) {
                    AnimatedImageDrawable ad = (AnimatedImageDrawable) d;
                    ad.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
                    ad.start();
                }
            } else {
                try (InputStream in = getAssets().open(file)) {
                    iv.setImageBitmap(BitmapFactory.decodeStream(in));   // first frame only
                }
            }
        } catch (Exception ignored) {
        }
    }

    private File photoFile() { return new File(getFilesDir(), "profile.jpg"); }
    private File photoTemp() { return new File(getFilesDir(), "profile_new.jpg"); }

    private void takePhoto() {
        try {
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", photoTemp());
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            i.putExtra("android.intent.extras.CAMERA_FACING", 1);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_CAMERA);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            Toast.makeText(this, "Cannot open camera", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == REQ_CAMERA && result == RESULT_OK && photoTemp().exists()) {
            photoFile().delete();
            photoTemp().renameTo(photoFile());
            loadAvatar();
        }
    }

    // Decode saved profile photo, rotated upright. null if none/broken.
    private Bitmap decodePhoto(int target) {
        File f = photoFile();
        if (!f.exists()) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= target && o.outHeight / (sample * 2) >= target) {
                sample *= 2;
            }
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (b == null) return null;
            int rot = 0;
            int ori = new ExifInterface(f.getAbsolutePath())
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL);
            if (ori == ExifInterface.ORIENTATION_ROTATE_90) rot = 90;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_180) rot = 180;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_270) rot = 270;
            if (rot != 0) {
                Matrix m = new Matrix();
                m.postRotate(rot);
                b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            }
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private void loadAvatar() {
        Bitmap b = decodePhoto(400);
        if (b != null) {
            avatar.setPadding(0, 0, 0, 0);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.setImageBitmap(b);
            return;
        }
        avatar.setPadding(dp(20), dp(20), dp(20), dp(20));
        avatar.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        avatar.setImageResource(android.R.drawable.ic_menu_camera);
    }

    // No photo -> camera. Photo saved -> show it, with Replace / Remove.
    private void onAvatarClick() {
        Bitmap big = decodePhoto(900);
        if (big == null) {
            takePhoto();
            return;
        }
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(big);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setPadding(dp(8), dp(8), dp(8), dp(8));
        new AlertDialog.Builder(this)
                .setTitle("Profile picture")
                .setView(iv)
                .setPositiveButton("Replace", (d, w) -> takePhoto())
                .setNegativeButton("Remove", (d, w) -> confirmRemovePhoto())
                .setNeutralButton("Close", null)
                .show();
    }

    private void confirmRemovePhoto() {
        new AlertDialog.Builder(this)
                .setTitle("Remove profile picture?")
                .setPositiveButton("Remove", (d, w) -> {
                    photoFile().delete();
                    loadAvatar();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
