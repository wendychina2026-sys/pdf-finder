package com.example.twobuttons;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class MarkActivity extends Activity {

    private ParcelFileDescriptor pfd;
    private PdfRenderer renderer;
    private final LruCache<Integer, Bitmap> cache = new LruCache<>(4);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String path = getIntent().getStringExtra("path");

        try {
            pfd = ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(pfd);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open PDF", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView name = new TextView(this);
        name.setText(new File(path).getName());
        name.setTypeface(null, Typeface.BOLD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        Button categories = new Button(this);
        categories.setText("Categories");
        categories.setOnClickListener(v ->
                startActivity(new Intent(this, CategoryActivity.class)));
        top.addView(name, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(categories);

        ListView pages = new ListView(this);
        pages.setAdapter(new PageAdapter());

        root.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(pages, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (renderer != null) renderer.close();
            if (pfd != null) pfd.close();
        } catch (Exception ignored) {
        }
    }

    private Bitmap render(int index) {
        Bitmap b = cache.get(index);
        if (b != null) return b;
        PdfRenderer.Page page = renderer.openPage(index);
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = (int) ((float) w * page.getHeight() / page.getWidth());
        b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.eraseColor(Color.WHITE);
        page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        page.close();
        cache.put(index, b);
        return b;
    }

    private class PageAdapter extends BaseAdapter {
        @Override public int getCount() { return renderer.getPageCount(); }
        @Override public Object getItem(int i) { return i; }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView label;
            ImageView image;
            if (convertView == null) {
                row = new LinearLayout(MarkActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(0, 8, 0, 8);
                label = new TextView(MarkActivity.this);
                image = new ImageView(MarkActivity.this);
                image.setAdjustViewBounds(true);
                row.addView(label);
                row.addView(image, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
            } else {
                row = (LinearLayout) convertView;
                label = (TextView) row.getChildAt(0);
                image = (ImageView) row.getChildAt(1);
            }
            label.setText("Page " + (i + 1) + " / " + getCount());
            image.setImageBitmap(render(i));
            return row;
        }
    }
}
