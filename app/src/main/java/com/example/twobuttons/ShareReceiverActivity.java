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
            Toast.makeText(this, "No PDF received", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        File dir = DataFolder.dir("Documents", new File(getExternalFilesDir(null), "shared"));
        if (!dir.exists()) dir.mkdirs();

        File first = null;
        int copied = 0;
        for (Uri u : uris) {
            File f = copy(u, dir);
            if (f != null) {
                copied++;
                if (first == null) first = f;
            }
        }

        if (first == null) {
            Toast.makeText(this, "Cannot read PDF", Toast.LENGTH_LONG).show();
        } else {
            if (copied > 1) {
                Toast.makeText(this, copied + " PDFs saved. Opening the first.",
                        Toast.LENGTH_LONG).show();
            }
            Intent i = new Intent(this, MarkActivity.class);
            i.putExtra("path", first.getAbsolutePath());
            startActivity(i);
        }
        finish();
    }

    private File copy(Uri uri, File dir) {
        try {
            String name = displayName(uri);
            if (name == null || name.isEmpty()) name = "shared_" + System.currentTimeMillis();
            name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (!name.toLowerCase().endsWith(".pdf")) name += ".pdf";
            // receive into a temporary file first: the same PDF is never stored twice in the folder
            File tmp = new File(dir, ".incoming_" + System.currentTimeMillis());
            try (InputStream is = getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(tmp)) {
                if (is == null) return null;
                byte[] buf = new byte[16384];
                int r;
                while ((r = is.read(buf)) > 0) os.write(buf, 0, r);
            }
            File same = DataFolder.findSameFile(dir, tmp);
            if (same != null) { tmp.delete(); return same; }
            File out = new File(dir, name);
            int n = 1;
            while (out.exists()) {      // same name but a different document: keep both, numbered
                out = new File(dir, name.replaceFirst("(?i)\\.pdf$", "") + "_" + n++ + ".pdf");
            }
            if (!tmp.renameTo(out)) { tmp.delete(); return null; }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private String displayName(Uri uri) {
        if ("content".equals(uri.getScheme())) {
            try (Cursor c = getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) return c.getString(0);
            } catch (Exception ignored) { }
        }
        return uri.getLastPathSegment();
    }
}
