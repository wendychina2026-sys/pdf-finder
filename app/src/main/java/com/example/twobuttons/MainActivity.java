package com.example.twobuttons;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
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
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_CAMERA = 21;

    private ImageView avatar;
    private EditText searchBox;
    private LinearLayout sections;
    private LinearLayout results;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.WHITE);
        sv.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));

        LinearLayout.LayoutParams wide = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        root.addView(buildHeader(), wide);
        root.addView(buildSearch());

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setVisibility(android.view.View.GONE);
        root.addView(results, wide);

        sections = new LinearLayout(this);
        sections.setOrientation(LinearLayout.VERTICAL);
        root.addView(sections, wide);

        sv.addView(root);
        scroll = sv;

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.WHITE);
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
        nav.setPadding(dp(8), dp(10), dp(8), dp(10));
        float r = dp(20);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF4F5FA);
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
        return nav;
    }

    private LinearLayout.LayoutParams navLp() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout navItem(int iconRes, String label, boolean active,
                                 android.view.View.OnClickListener click) {
        int color = active ? CaseViews.NAVY : 0xFF333344;
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding(0, dp(4), 0, dp(4));
        ImageView ic = CaseViews.icon(this, iconRes, color, 24);
        item.addView(ic);
        TextView t = CaseViews.text(this, label, 11, color, active);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
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
        box.setBackground(CaseViews.box(this, Color.WHITE, 10, 0xFFD9DBE6, 1));

        box.addView(CaseViews.icon(this, R.drawable.ic_search, CaseViews.GREY, 22));

        searchBox = new EditText(this);
        searchBox.setHint("Search by case name");
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
        String q = searchBox.getText().toString().trim();
        if (q.isEmpty()) showSections(); else showResults(q);
    }

    private void showResults(String q) {
        sections.setVisibility(android.view.View.GONE);
        results.setVisibility(android.view.View.VISIBLE);
        results.removeAllViews();
        results.addView(plainHeader("Search results"));
        List<CaseItems.Item> found = CaseItems.search(this, q);
        if (found.isEmpty()) {
            results.addView(CaseViews.emptyNote(this, "No case found"));
            return;
        }
        for (CaseItems.Item it : found) {
            results.addView(CaseViews.caseRow(this, it,
                    v -> CaseViews.openPdf(this, it.path)), CaseViews.lp(this, 0, 10));
        }
    }

    private void showSections() {
        results.setVisibility(android.view.View.GONE);
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
        List<CaseItems.Item> today = CaseItems.today(this);
        if (today.isEmpty()) {
            sections.addView(CaseViews.emptyNote(this, "Nothing scheduled today"));
        } else {
            int n = Math.min(3, today.size());
            for (int i = 0; i < n; i++) {
                CaseItems.Item it = today.get(i);
                sections.addView(CaseViews.caseRow(this, it,
                        v -> CaseViews.openDetails(this, it.path)), CaseViews.lp(this, 0, 10));
            }
            if (today.size() > n) {
                sections.addView(CaseViews.text(this, "+" + (today.size() - n) + " more",
                        13, CaseViews.GREY, false));
            }
        }
    }

    private TextView plainHeader(String title) {
        TextView t = CaseViews.text(this, title, 18, Color.BLACK, true);
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
        avatar.setOnClickListener(v -> takePhoto());
        loadAvatar();

        TextView icon = new TextView(this);
        icon.setTextSize(28);
        icon.setPadding(dp(16), 0, dp(8), 0);
        tickIcon(icon);

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("EEE, dd MMM yyyy  hh:mm:ss a");
        clock.setFormat24Hour("EEE, dd MMM yyyy  hh:mm:ss a");
        clock.setTextSize(16);
        clock.setTypeface(null, Typeface.BOLD);

        row.addView(avatar, new LinearLayout.LayoutParams(dp(72), dp(72)));
        row.addView(icon);
        row.addView(clock);
        return row;
    }

    // day / noon / evening / night logo
    private void tickIcon(TextView icon) {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String sym;
        if (h >= 5 && h < 12) sym = "\uD83C\uDF24\uFE0F";        // day
        else if (h >= 12 && h < 16) sym = "\u2600\uFE0F";         // noon
        else if (h >= 16 && h < 20) sym = "\uD83C\uDF07";         // evening
        else sym = "\uD83C\uDF19";                                // night
        icon.setText(sym);
        icon.postDelayed(() -> {
            if (icon.isAttachedToWindow()) tickIcon(icon);
        }, 30000);
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

    private void loadAvatar() {
        File f = photoFile();
        if (f.exists()) {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                int sample = 1;
                while (o.outWidth / (sample * 2) >= 400 && o.outHeight / (sample * 2) >= 400) {
                    sample *= 2;
                }
                o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                if (b != null) {
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
                    avatar.setPadding(0, 0, 0, 0);
                    avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    avatar.setImageBitmap(b);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        avatar.setPadding(dp(20), dp(20), dp(20), dp(20));
        avatar.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        avatar.setImageResource(android.R.drawable.ic_menu_camera);
    }

}
