package com.example.twobuttons;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tiny file server for "Transfer Files". The sender picks files, this server shares exactly those files
 * on the local Wi-Fi / hotspot, and the QR code carries address, port and a one-time random token.
 * Without the token (only visible in the QR) the server answers nothing.
 */
final class TransferServer {

    static final class Item {
        final Uri uri;
        final String name;
        final long size;

        Item(Uri uri, String name, long size) {
            this.uri = uri;
            this.name = name;
            this.size = size;
        }
    }

    private final Context app;
    private final List<Item> items;
    private final String token;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private ServerSocket server;
    private volatile boolean running;

    TransferServer(Context c, List<Item> items) {
        this.app = c.getApplicationContext();
        this.items = new ArrayList<>(items);
        byte[] b = new byte[10];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        this.token = sb.toString();
    }

    String token() { return token; }

    /** Starts listening on a free port and returns that port. */
    int start() throws Exception {
        server = new ServerSocket(0);
        running = true;
        Thread t = new Thread(this::acceptLoop, "transfer-accept");
        t.setDaemon(true);
        t.start();
        return server.getLocalPort();
    }

    void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) { }
        pool.shutdownNow();
    }

    private void acceptLoop() {
        while (running) {
            try {
                final Socket s = server.accept();
                pool.execute(() -> handle(s));
            } catch (Exception e) {
                if (!running) break;
            }
        }
    }

    private void handle(Socket s) {
        try (Socket sock = s) {
            sock.setSoTimeout(20000);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(sock.getInputStream(), StandardCharsets.ISO_8859_1));
            String line = in.readLine();
            if (line == null) return;
            String h;
            while ((h = in.readLine()) != null && !h.isEmpty()) { /* skip headers */ }
            OutputStream out = new BufferedOutputStream(sock.getOutputStream());

            String[] parts = line.split(" ");
            if (parts.length < 2 || !"GET".equals(parts[0])) {
                reply(out, 405, "text/plain", "Method not allowed".getBytes(StandardCharsets.UTF_8), -1);
                return;
            }
            String target = parts[1];
            String path = target;
            String query = "";
            int q = target.indexOf('?');
            if (q >= 0) {
                path = target.substring(0, q);
                query = target.substring(q + 1);
            }
            if (!("t=" + token).equals(query)) {
                reply(out, 403, "text/plain", "Forbidden".getBytes(StandardCharsets.UTF_8), -1);
                return;
            }

            if (path.equals("/list")) {
                JSONArray arr = new JSONArray();
                for (Item it : items) {
                    JSONObject o = new JSONObject();
                    o.put("name", it.name);
                    o.put("size", it.size);
                    arr.put(o);
                }
                reply(out, 200, "application/json", arr.toString().getBytes(StandardCharsets.UTF_8), -1);
            } else if (path.startsWith("/file/")) {
                int idx;
                try { idx = Integer.parseInt(path.substring(6)); } catch (Exception e) { idx = -1; }
                if (idx < 0 || idx >= items.size()) {
                    reply(out, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8), -1);
                    return;
                }
                Item it = items.get(idx);
                try (InputStream is = app.getContentResolver().openInputStream(it.uri)) {
                    if (is == null) {
                        reply(out, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8), -1);
                        return;
                    }
                    reply(out, 200, "application/octet-stream", null, it.size);
                    byte[] buf = new byte[16384];
                    int r;
                    while ((r = is.read(buf)) > 0) out.write(buf, 0, r);
                    out.flush();
                }
            } else {
                reply(out, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8), -1);
            }
        } catch (Exception ignored) {
            // a dropped connection just ends this one transfer
        }
    }

    /** Writes the headers; body (when not null) is sent right away. length is used when body is null. */
    private static void reply(OutputStream out, int code, String type, byte[] body, long length) throws Exception {
        String status = code == 200 ? "OK" : code == 403 ? "Forbidden" : code == 404 ? "Not Found" : "Error";
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(code).append(' ').append(status).append("\r\n");
        sb.append("Content-Type: ").append(type).append("\r\n");
        long len = body != null ? body.length : length;
        if (len >= 0) sb.append("Content-Length: ").append(len).append("\r\n");
        sb.append("Connection: close\r\n\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (body != null) out.write(body);
        out.flush();
    }

    /** Name and size of a picked document. */
    static Item describe(Context c, Uri uri) {
        String name = null;
        long size = -1;
        try (Cursor cur = c.getContentResolver().query(uri, null, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                int ni = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int si = cur.getColumnIndex(OpenableColumns.SIZE);
                if (ni >= 0) name = cur.getString(ni);
                if (si >= 0 && !cur.isNull(si)) size = cur.getLong(si);
            }
        } catch (Exception ignored) { }
        if (name == null || name.isEmpty()) name = "file_" + System.currentTimeMillis();
        return new Item(uri, name, size);
    }

    /** This phone's address on Wi-Fi or its own hotspot; null when not connected to any local network. */
    static String localIp() {
        try {
            String best = null;
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String n = ni.getName();
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        if (n.startsWith("wlan") || n.startsWith("ap") || n.startsWith("swlan")) {
                            return a.getHostAddress();
                        }
                        if (best == null) best = a.getHostAddress();
                    }
                }
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }
}
