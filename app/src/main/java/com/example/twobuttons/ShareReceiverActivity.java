package com.example.twobuttons;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/** Receives PDFs from the system Share / Open-with menu, copies the first one
 *  into app storage and opens it in the Category marker. */
public class ShareReceiverActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        handle(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
    }

    private void handle(Intent in) {
        ArrayList<Uri> uris = new ArrayList<>();
        String action = in == null ? null : in.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri u = in.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) uris.add(u);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> l = in.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (l != null) uris.addAll(l);
        } else if (Intent.ACTION_VIEW.equals(action) && in.getData() != null) {
            uris.add(in.getData());
        }

        if (uris.isEmpty()) {
            Toast.makeText(this, "No file received", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        File dir = DataFolder.dir("Documents", new File(getExternalFilesDir(null), "shared"));
        if (!dir.exists()) dir.mkdirs();

        File first = null;          // first PDF -> Category marker
        File firstWord = null;      // first Word file -> Upload Legal Document popup
        int copied = 0, wordCount = 0;
        for (Uri u : uris) {
            String type = getContentResolver().getType(u);
            String nm = u.getLastPathSegment() == null ? "" : u.getLastPathSegment().toLowerCase(java.util.Locale.ROOT);
            boolean word = (type != null && (type.equals("application/msword") || type.contains("wordprocessingml")))
                    || nm.endsWith(".doc") || nm.endsWith(".docx");
            boolean image = (type != null && type.startsWith("image/"))
                    || nm.matches(".*\\.(jpe?g|png|webp|gif|bmp|heic|heif)$");
            word = word || image;       // an image, like a Word file, has no pages to mark
            File f = word ? DataFolder.importFile(this, u, dir) : DataFolder.importPdf(this, u, dir);
            if (f == null) continue;
            if (!Intent.ACTION_VIEW.equals(action)) DataFolder.deleteOriginal(this, u, f);   // shared: move, not copy
            if (word) {
                wordCount++;
                if (firstWord == null) firstWord = f;
            } else {
                copied++;
                if (first == null) first = f;
            }
        }

        if (first == null && firstWord == null) {
            Toast.makeText(this, "Cannot read the shared file", Toast.LENGTH_LONG).show();
        } else if (first != null) {
            if (copied > 1) {
                Toast.makeText(this, copied + " PDFs saved. Opening the first.",
                        Toast.LENGTH_LONG).show();
            }
            if (wordCount > 0) {
                Toast.makeText(this, wordCount + " file(s) saved in the Documents folder.",
                        Toast.LENGTH_LONG).show();
            }
            Intent i = new Intent(this, MarkActivity.class);
            i.putExtra("path", first.getAbsolutePath());
            startActivity(i);
        } else {
            if (wordCount > 1) {
                Toast.makeText(this, wordCount + " files saved. Opening the first; the rest are in the Documents folder.",
                        Toast.LENGTH_LONG).show();
            }
            // a Word file or image has no pages to mark: choose its name, category and matter in Upload Legal Document
            Intent i = new Intent(this, NewCaseActivity.class);
            i.putExtra(NewCaseActivity.EXTRA_UPLOAD_PDF, firstWord.getAbsolutePath());
            startActivity(i);
        }
        finish();
    }
}
