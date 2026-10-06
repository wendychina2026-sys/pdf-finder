package com.example.twobuttons;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private List<Pdf> allPdfs = new ArrayList<>();
    private PdfAdapter adapter;
    private PdfActions actions;
    private PermissionHelper perms;
    private EditText searchBox;
    private TextView status;
    private Button findButton;
    private boolean scanned = false;
    private boolean waitingForPermission = false;
    private static final int REQ_CAMERA = 21;
    private ImageView avatar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        perms = new PermissionHelper(this);
        adapter = new PdfAdapter(this);
        actions = new PdfActions(this, adapter, new PdfActions.Host() {
            @Override public List<Pdf> pdfs() { return allPdfs; }
            @Override public void onListChanged() { applyFilter(); }
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        findButton = new Button(this);
        findButton.setText("Find PDFs");
        findButton.setOnClickListener(v -> startScan());

        searchBox = new EditText(this);
        searchBox.setHint("Search PDF name");
        searchBox.setSingleLine(true);
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (scanned) applyFilter();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        status = new TextView(this);
        status.setText("Tap Find PDFs to start");
        status.setPadding(0, 16, 0, 16);

        ListView list = new ListView(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) ->
                actions.onItemClick(adapter.getItem(pos)));
        list.setOnItemLongClickListener((parent, view, pos, id) -> {
            actions.onItemLongClick(adapter.getItem(pos));
            return true;
        });

        LinearLayout.LayoutParams wide = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        root.addView(buildHeader(), wide);

        root.addView(actions.buildSelectionBar(), wide);
        root.addView(findButton, wide);

        Button markedButton = new Button(this);
        markedButton.setText("Marked pages");
        markedButton.setOnClickListener(v ->
                startActivity(new Intent(this, MarkedActivity.class)));
        root.addView(markedButton, wide);

        Button filesButton = new Button(this);
        filesButton.setText("Marked PDFs");
        filesButton.setOnClickListener(v ->
                startActivity(new Intent(this, MarkedFilesActivity.class)));
        root.addView(filesButton, wide);

        Button searchCatButton = new Button(this);
        searchCatButton.setText("Search by category");
        searchCatButton.setOnClickListener(v ->
                startActivity(new Intent(this, CategorySearchActivity.class)));
        root.addView(searchCatButton, wide);

        Button advButton = new Button(this);
        advButton.setText("Advanced search");
        advButton.setOnClickListener(v ->
                startActivity(new Intent(this, AdvancedSearchActivity.class)));
        root.addView(advButton, wide);
        root.addView(searchBox, wide);
        root.addView(status, wide);
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForPermission && perms.hasAccess()) {
            waitingForPermission = false;
            scan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] results) {
        super.onRequestPermissionsResult(code, p, results);
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            scan();
        } else {
            status.setText("Permission denied - cannot search for PDFs");
        }
    }

    @Override
    public void onBackPressed() {
        if (!actions.handleBack()) super.onBackPressed();
    }

    private void startScan() {
        if (perms.hasAccess()) {
            scan();
        } else {
            waitingForPermission = Build.VERSION.SDK_INT >= 30;
            perms.request();
        }
    }

    private void scan() {
        status.setText("Scanning...");
        findButton.setEnabled(false);
        PdfScanner.scan(this, found -> {
            allPdfs = found;
            scanned = true;
            findButton.setEnabled(true);
            applyFilter();
        });
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

    private void applyFilter() {
        int shown = adapter.filter(allPdfs, searchBox.getText().toString());
        status.setText(shown + " of " + allPdfs.size() + " PDFs");
        actions.refresh();
    }
}
