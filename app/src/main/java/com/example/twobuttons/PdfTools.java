package com.example.twobuttons;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Camera photos -> PDF pages, and edits (remove pages / add photos) to an existing PDF. */
final class PdfTools {
    private PdfTools() {}

    static File newFile(Context c, String prefix, String ext) {
        File dir = DataFolder.dir("Photos", c.getExternalFilesDir("case_photos"));
        if (dir == null) dir = c.getCacheDir();
        dir.mkdirs();
        return new File(dir, prefix + new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date()) + ext);
    }

    /** A photo at a sane size, turned upright by its EXIF rotation. */
    static Bitmap decode(File f, int maxSide) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int big = Math.max(o.outWidth, o.outHeight), ss = 1;
        while (big / ss > maxSide) ss *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = ss;
        Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        if (b == null) return null;
        int ori = new android.media.ExifInterface(f.getAbsolutePath())
                .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
        int deg = ori == 6 ? 90 : ori == 3 ? 180 : ori == 8 ? 270 : 0;
        if (deg == 0) return b;
        Matrix mx = new Matrix();
        mx.postRotate(deg);
        Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), mx, true);
        if (r != b) b.recycle();
        return r;
    }

    /** One A4 page per photo (landscape page for a landscape photo). */
    static void addImagePages(PDDocument doc, List<File> shots) throws IOException {
        for (File shot : shots) {
            Bitmap bmp = decode(shot, 2400);
            if (bmp == null) continue;
            PDRectangle a4 = PDRectangle.A4;
            PDRectangle size = bmp.getWidth() > bmp.getHeight()
                    ? new PDRectangle(a4.getHeight(), a4.getWidth()) : a4;
            PDPage page = new PDPage(size);
            doc.addPage(page);
            float m = 18f;
            float sc = Math.min((size.getWidth() - 2 * m) / bmp.getWidth(), (size.getHeight() - 2 * m) / bmp.getHeight());
            float w = bmp.getWidth() * sc, h = bmp.getHeight() * sc;
            PDImageXObject img = JPEGFactory.createFromImage(doc, bmp, 0.8f);
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            cs.drawImage(img, (size.getWidth() - w) / 2f, (size.getHeight() - h) / 2f, w, h);
            cs.close();
            bmp.recycle();
        }
    }

    static File makePdf(Context c, List<File> shots) throws IOException {
        PDFBoxResourceLoader.init(c.getApplicationContext());
        File out = newFile(c, "Scan_", ".pdf");
        PDDocument doc = new PDDocument();
        try {
            addImagePages(doc, shots);
            if (doc.getNumberOfPages() == 0) throw new IOException("no pages");
            doc.save(out);
        } finally {
            doc.close();
        }
        return out;
    }

    /** Copy of {@code src} without the pages marked in {@code removed}, plus the photos as new last pages. */
    static File editPdf(Context c, Uri src, boolean[] removed, List<File> add) throws IOException {
        PDFBoxResourceLoader.init(c.getApplicationContext());
        File out = newFile(c, "Scan_", ".pdf");
        PDDocument doc = null;
        try (InputStream in = c.getContentResolver().openInputStream(src)) {
            if (in == null) throw new IOException("cannot open");
            doc = PDDocument.load(in);
            for (int i = removed.length - 1; i >= 0; i--) {
                if (removed[i]) doc.removePage(i);
            }
            addImagePages(doc, add);
            if (doc.getNumberOfPages() == 0) throw new IOException("no pages");
            doc.save(out);
        } finally {
            if (doc != null) doc.close();
        }
        return out;
    }

    static String fileName(Context c, Uri u) {
        try (Cursor q = c.getContentResolver().query(u, null, null, null, null)) {
            if (q != null && q.moveToFirst()) {
                int i = q.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0 && !q.isNull(i)) return q.getString(i);
            }
        } catch (Exception ignored) {
            // fall through
        }
        String s = u.getLastPathSegment();
        return s == null ? "File" : s;
    }

    static String sizeLabel(Context c, Uri u) {
        long b = 0;
        try (Cursor q = c.getContentResolver().query(u, null, null, null, null)) {
            if (q != null && q.moveToFirst()) {
                int i = q.getColumnIndex(OpenableColumns.SIZE);
                if (i >= 0 && !q.isNull(i)) b = q.getLong(i);
            }
        } catch (Exception ignored) {
            // no size
        }
        if (b <= 0) return "";
        if (b < 1024 * 1024) return String.format(Locale.US, "%.1f KB", b / 1024.0);
        return String.format(Locale.US, "%.1f MB", b / (1024.0 * 1024.0));
    }
}
