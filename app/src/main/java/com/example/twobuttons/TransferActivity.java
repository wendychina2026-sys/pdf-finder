package com.example.twobuttons;

import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Transfer Files. The giver picks files and shows a QR code; the receiver scans it and the two phones
 * connect directly over the same Wi-Fi (or one phone's hotspot). Received files go to the app's
 * Documents folder.
 */
public class TransferActivity extends BaseActivity {

    private static final int REQ_PICK = 4101;
    private static final int REQ_EXPLORER = 4102;
    private static final String PREFIX = "PDFFINDER";

    private LinearLayout root;
    private TransferServer server;
    private boolean onHome = true;

    // receiver widgets
    private TextView status;
    private ProgressBar bar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Transfer Files");
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(AppTheme.bg(this));
        sv.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = LegalUi.dp(this, 20);
        root.setPadding(p, p, p, p);
        sv.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        setContentView(sv);
        showHome();
    }

    @Override
    protected void onDestroy() {
        stopServer();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (!onHome) {
            stopServer();
            showHome();
        } else {
            super.onBackPressed();
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // ------------------------------------------------------------------ home

    private void header(String title) {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = CaseViews.icon(this, R.drawable.ic_arrow_back, AppTheme.text(this), 28);
        back.setPadding(LegalUi.dp(this, 6), LegalUi.dp(this, 6), LegalUi.dp(this, 6), LegalUi.dp(this, 6));
        back.setContentDescription("Back");
        back.setOnClickListener(v -> onBackPressed());
        top.addView(back, new LinearLayout.LayoutParams(LegalUi.dp(this, 44), LegalUi.dp(this, 44)));
        TextView t = CaseViews.text(this, title, 22, AppTheme.text(this), true);
        t.setPadding(LegalUi.dp(this, 8), 0, 0, 0);
        top.addView(t);
        root.addView(top, CaseViews.lp(this, 0, 14));
    }

    private LinearLayout bigButton(int icon, String title, String sub, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(LegalUi.dp(this, 16), LegalUi.dp(this, 18), LegalUi.dp(this, 16), LegalUi.dp(this, 18));
        row.setBackground(CaseViews.box(this, AppTheme.card(this), 14, AppTheme.cardStroke(this), 1));
        row.addView(CaseViews.icon(this, icon, AppTheme.accent(this), 32));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(LegalUi.dp(this, 14), 0, 0, 0);
        col.addView(CaseViews.text(this, title, 17, AppTheme.text(this), true));
        col.addView(CaseViews.text(this, sub, 13, AppTheme.sub(this), false));
        row.addView(col, LegalUi.weight());
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private void showHome() {
        onHome = true;
        root.removeAllViews();
        header("Transfer Files");
        root.addView(CaseViews.text(this,
                "Both phones must be on the same Wi-Fi, or one phone's hotspot. "
                        + "The giver creates a QR code, the receiver scans it.",
                14, AppTheme.sub(this), false), CaseViews.lp(this, 0, 16));
        root.addView(bigButton(R.drawable.ic_upload, "Send files",
                "Pick files and show a QR code", this::pickFiles), CaseViews.lp(this, 0, 12));
        root.addView(bigButton(R.drawable.ic_camera, "Receive files",
                "Scan the giver's QR code", this::startScan), CaseViews.lp(this, 0, 12));
    }

    // ------------------------------------------------------------------ giver

    private void pickFiles() {
        Intent i = new Intent(this, PhoneFilesActivity.class);
        i.putExtra(PhoneFilesActivity.EXTRA_PICK, true);
        startActivityForResult(i, REQ_EXPLORER);
    }

    private void showQr(List<TransferServer.Item> items) {
        String ip = TransferServer.localIp();
        if (ip == null) {
            Toast.makeText(this, "Not connected to Wi-Fi. Connect both phones to the same Wi-Fi or hotspot.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        stopServer();
        int port;
        TransferServer s = new TransferServer(this, items);
        try {
            port = s.start();
        } catch (Exception e) {
            Toast.makeText(this, "Could not start sharing: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        server = s;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String payload = PREFIX + "|" + ip + "|" + port + "|" + s.token();
        Bitmap bmp;
        try {
            bmp = new BarcodeEncoder().encodeBitmap(payload, BarcodeFormat.QR_CODE, 720, 720);
        } catch (Exception e) {
            stopServer();
            Toast.makeText(this, "Could not create the QR code", Toast.LENGTH_LONG).show();
            return;
        }

        onHome = false;
        root.removeAllViews();
        header("Send files");
        root.addView(CaseViews.text(this,
                items.size() + (items.size() == 1 ? " file is ready." : " files are ready.")
                        + " Ask the receiver to tap Receive files and scan this code.",
                14, AppTheme.sub(this), false), CaseViews.lp(this, 0, 14));

        ImageView qr = new ImageView(this);
        qr.setImageBitmap(bmp);
        qr.setBackgroundColor(0xFFFFFFFF);
        int pad = LegalUi.dp(this, 12);
        qr.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(
                LegalUi.dp(this, 280), LegalUi.dp(this, 280));
        qlp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(qr, qlp);

        TextView addr = CaseViews.text(this, "Your address: " + ip + ":" + port, 13, AppTheme.sub(this), false);
        addr.setGravity(Gravity.CENTER);
        root.addView(addr, CaseViews.lp(this, 10, 6));
        TextView keep = CaseViews.text(this,
                "Keep this screen open until the receiver has finished.", 13, AppTheme.sub(this), false);
        keep.setGravity(Gravity.CENTER);
        root.addView(keep, CaseViews.lp(this, 0, 14));

        TextView stop = CaseViews.text(this, "Stop sharing", 16, AppTheme.isDark(this) ? 0xFF121212 : 0xFFFFFFFF, true);
        stop.setGravity(Gravity.CENTER);
        stop.setPadding(0, LegalUi.dp(this, 14), 0, LegalUi.dp(this, 14));
        stop.setBackground(CaseViews.box(this, AppTheme.accent(this), 12, 0, 0));
        stop.setOnClickListener(v -> onBackPressed());
        root.addView(stop, CaseViews.lp(this, 6, 0));
    }

    // ------------------------------------------------------------------ receiver

    private void startScan() {
        new IntentIntegrator(this)
                .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                .setPrompt("Scan the QR code shown on the giver's phone")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
                .initiateScan();
    }

    private void showReceiving() {
        onHome = false;
        root.removeAllViews();
        header("Receive files");
        status = CaseViews.text(this, "Connecting...", 15, AppTheme.text(this), false);
        root.addView(status, CaseViews.lp(this, 6, 10));
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        root.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LegalUi.dp(this, 12)));
    }

    private void say(final String msg) {
        runOnUiThread(() -> { if (status != null) status.setText(msg); });
    }

    private void progress(final int pct) {
        runOnUiThread(() -> { if (bar != null) bar.setProgress(pct); });
    }

    private void receive(String content) {
        String[] p = content == null ? new String[0] : content.split("\\|");
        if (p.length != 4 || !PREFIX.equals(p[0])) {
            Toast.makeText(this, "This is not a file transfer QR code", Toast.LENGTH_LONG).show();
            return;
        }
        final String base = "http://" + p[1] + ":" + p[2];
        final String token = p[3];
        showReceiving();
        final File dir = DataFolder.documentsDir(this);
        new Thread(() -> {
            int done = 0;
            try {
                HttpURLConnection lc = (HttpURLConnection) new URL(base + "/list?t=" + token).openConnection();
                lc.setConnectTimeout(8000);
                lc.setReadTimeout(15000);
                if (lc.getResponseCode() != 200) throw new Exception("Giver refused the connection");
                StringBuilder sb = new StringBuilder();
                try (InputStream is = lc.getInputStream()) {
                    byte[] buf = new byte[4096];
                    int r;
                    while ((r = is.read(buf)) > 0) sb.append(new String(buf, 0, r, "UTF-8"));
                }
                JSONArray arr = new JSONArray(sb.toString());
                int n = arr.length();
                for (int i = 0; i < n; i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String name = o.optString("name", "file_" + i).replaceAll("[\\\\/:*?\"<>|]", "_");
                    long size = o.optLong("size", -1);
                    say("Receiving " + (i + 1) + " of " + n + ": " + name);
                    progress(0);
                    HttpURLConnection fc = (HttpURLConnection) new URL(base + "/file/" + i + "?t=" + token)
                            .openConnection();
                    fc.setConnectTimeout(8000);
                    fc.setReadTimeout(20000);
                    if (fc.getResponseCode() != 200) throw new Exception("Could not get " + name);
                    File tmp = new File(dir, ".incoming_" + System.currentTimeMillis());
                    long got = 0;
                    try (InputStream is = fc.getInputStream(); OutputStream os = new FileOutputStream(tmp)) {
                        byte[] buf = new byte[16384];
                        int r;
                        while ((r = is.read(buf)) > 0) {
                            os.write(buf, 0, r);
                            got += r;
                            if (size > 0) progress((int) (got * 100 / size));
                        }
                    }
                    int dot = name.lastIndexOf('.');
                    String stem = dot > 0 ? name.substring(0, dot) : name;
                    String ext = dot > 0 ? name.substring(dot) : "";
                    File out = new File(dir, name);
                    for (int k = 1; out.exists(); k++) out = new File(dir, stem + "_" + k + ext);
                    if (!tmp.renameTo(out)) {
                        tmp.delete();
                        throw new Exception("Could not save " + name);
                    }
                    done++;
                    progress(100);
                }
                final int total = done;
                say("Done. " + total + (total == 1 ? " file" : " files") + " saved in Documents.");
            } catch (Exception e) {
                say("Failed after " + done + " file(s): " + e.getMessage()
                        + "\nCheck that both phones are on the same Wi-Fi and the giver's screen is still open.");
            }
        }, "transfer-receive").start();
    }

    // ------------------------------------------------------------------ results

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_EXPLORER) {
            if (resultCode != RESULT_OK || data == null) return;
            ArrayList<String> paths = data.getStringArrayListExtra(PhoneFilesActivity.EXTRA_PATHS);
            if (paths == null || paths.isEmpty()) return;
            List<TransferServer.Item> chosen = new ArrayList<>();
            for (String path : paths) {
                File f = new File(path);
                if (f.isFile()) chosen.add(new TransferServer.Item(Uri.fromFile(f), f.getName(), f.length()));
            }
            if (!chosen.isEmpty()) showQr(chosen);
            return;
        }
        if (requestCode == REQ_PICK) {
            if (resultCode != RESULT_OK || data == null) return;
            List<Uri> uris = new ArrayList<>();
            ClipData cd = data.getClipData();
            if (cd != null) {
                for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (uris.isEmpty()) return;
            List<TransferServer.Item> items = new ArrayList<>();
            for (Uri u : uris) items.add(TransferServer.describe(this, u));
            showQr(items);
            return;
        }
        IntentResult r = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (r != null) {
            if (r.getContents() != null) receive(r.getContents());
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
