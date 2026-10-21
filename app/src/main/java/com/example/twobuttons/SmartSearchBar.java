package com.example.twobuttons;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.util.ArrayList;

/**
 * The app's search bar: magnifier, text box, then three tools on the right.
 * <ul>
 *   <li>Mic: speak, the words go into the box (system voice typing).</li>
 *   <li>Camera: photograph some printed text (a case number, a title); the text read from the
 *       photo goes into the box (on-device text recognition).</li>
 *   <li>Scanner: scan a QR code or barcode; its value goes into the box.</li>
 * </ul>
 * The host activity must forward onActivityResult to {@link #onActivityResult}.
 */
final class SmartSearchBar {

    private static final int REQ_VOICE = 9101;
    private static final int REQ_PHOTO = 9102;

    private final Activity a;
    private final LinearLayout frame;
    private final RoundEdit edit;

    SmartSearchBar(Activity a, String hint) {
        this.a = a;
        frame = new LinearLayout(a);
        frame.setOrientation(LinearLayout.HORIZONTAL);
        frame.setGravity(Gravity.CENTER_VERTICAL);
        frame.setPadding(dp(10), 0, dp(4), 0);
        frame.setBackground(CaseViews.box(a, AppTheme.field(a), 10, AppTheme.fieldStroke(a), 1));

        frame.addView(CaseViews.icon(a, R.drawable.ic_search, AppTheme.sub(a), 18));

        edit = new RoundEdit(a);
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setEllipsize(TextUtils.TruncateAt.END);
        edit.setTextSize(13);
        edit.setBackground(null);
        edit.setPadding(dp(8), dp(8), 0, dp(8));
        frame.addView(edit, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        frame.addView(tool(R.drawable.ic_mic, "Voice search", v -> startVoice()));
        frame.addView(tool(R.drawable.ic_camera, "Search from a photo of text", v -> startPhoto()));
        frame.addView(tool(R.drawable.ic_scanner, "Scan QR code or barcode", v -> startScan()));
    }

    View view() { return frame; }

    EditText edit() { return edit; }

    private int dp(int v) { return CaseViews.dp(a, v); }

    private ImageView tool(int icon, String description, View.OnClickListener click) {
        ImageView v = CaseViews.icon(a, icon, AppTheme.accent(a), 20);
        v.setPadding(dp(8), dp(8), dp(8), dp(8));
        v.setContentDescription(description);
        v.setOnClickListener(click);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(36)));
        return v;
    }

    private void put(String text) {
        if (text == null) return;
        String t = text.replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) {
            Toast.makeText(a, "Nothing found", Toast.LENGTH_SHORT).show();
            return;
        }
        if (t.length() > 80) t = t.substring(0, 80).trim();
        edit.setText(t);
        edit.setSelection(edit.getText().length());
    }

    // ---------- mic ----------

    private void startVoice() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say what to search for");
        try {
            a.startActivityForResult(i, REQ_VOICE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, "Voice input is not available on this phone", Toast.LENGTH_LONG).show();
        }
    }

    // ---------- camera (text from a photo) ----------

    private File photoFile() { return new File(a.getCacheDir(), "search_photo.jpg"); }

    private void startPhoto() {
        try {
            photoFile().delete();
            Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", photoFile());
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivityForResult(i, REQ_PHOTO);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            Toast.makeText(a, "Cannot open camera", Toast.LENGTH_SHORT).show();
        }
    }

    private void readPhoto() {
        File f = photoFile();
        if (!f.exists() || f.length() == 0) return;
        try {
            InputImage img = InputImage.fromFilePath(a, Uri.fromFile(f));
            Toast.makeText(a, "Reading text...", Toast.LENGTH_SHORT).show();
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(img)
                    .addOnSuccessListener(r -> { put(r.getText()); f.delete(); })
                    .addOnFailureListener(e -> {
                        Toast.makeText(a, "Could not read text from the photo", Toast.LENGTH_LONG).show();
                        f.delete();
                    });
        } catch (Exception e) {
            Toast.makeText(a, "Could not open the photo", Toast.LENGTH_LONG).show();
        }
    }

    // ---------- scanner (QR / barcode) ----------

    private void startScan() {
        GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(a,
                new GmsBarcodeScannerOptions.Builder().enableAutoZoom().build());
        scanner.startScan()
                .addOnSuccessListener((Barcode b) -> put(b.getRawValue()))
                .addOnFailureListener(e -> Toast.makeText(a,
                        "Scanner not available. Check that Google Play services is up to date.",
                        Toast.LENGTH_LONG).show());
    }

    // ---------- results ----------

    /** Returns true when the result belonged to this bar. */
    boolean onActivityResult(int req, int res, Intent data) {
        if (req == REQ_VOICE) {
            if (res == Activity.RESULT_OK && data != null) {
                ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (r != null && !r.isEmpty()) put(r.get(0));
            }
            return true;
        }
        if (req == REQ_PHOTO) {
            if (res == Activity.RESULT_OK) readPhoto();
            return true;
        }
        return false;
    }
}
