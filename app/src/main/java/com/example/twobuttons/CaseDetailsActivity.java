package com.example.twobuttons;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class CaseDetailsActivity extends Activity {

    private static final String SELECT = "-- Select --";
    private static final SimpleDateFormat ISO = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private String path;
    private DetailsStore store;
    private Spinner caseSpin;
    private Spinner subSpin;
    private Spinner courtSpin;
    private Spinner yearSpin;
    private Button nextBtn;
    private AutoCompleteTextView lawyer;
    private String nextDate = "";
    private String pendingSub = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Case details");
        path = getIntent().getStringExtra("path");
        store = new DetailsStore(this);
        DetailsStore.Details saved = store.get(path);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        sv.addView(root);

        TextView file = new TextView(this);
        file.setText(new File(path).getName());
        file.setTextSize(16);
        file.setTypeface(null, Typeface.BOLD);
        root.addView(file);

        caseSpin = spinner(DetailsStore.CASE_TYPES);
        subSpin = spinner(new String[0]);
        courtSpin = spinner(DetailsStore.COURTS);
        yearSpin = spinner(years());

        root.addView(label("Case type"));
        root.addView(caseSpin);
        root.addView(label("Sub case type"));
        root.addView(subSpin);
        root.addView(label("Court"));
        root.addView(courtSpin);
        root.addView(label("Year of filing"));
        root.addView(yearSpin);

        root.addView(label("Next date (after today)"));
        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        nextBtn = new Button(this);
        nextBtn.setAllCaps(false);
        nextBtn.setOnClickListener(v -> pickDate());
        Button clear = new Button(this);
        clear.setText("Clear");
        clear.setOnClickListener(v -> {
            nextDate = "";
            updateNextButton();
        });
        dateRow.addView(nextBtn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        dateRow.addView(clear);
        root.addView(dateRow);

        lawyer = new AutoCompleteTextView(this);
        lawyer.setHint("Lawyer name");
        lawyer.setSingleLine(true);
        lawyer.setThreshold(1);
        lawyer.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, store.lawyers()));
        root.addView(label("Lawyer name"));
        root.addView(lawyer);

        Button save = new Button(this);
        save.setText("Save");
        save.setOnClickListener(v -> save());
        root.addView(save);

        if (saved != null) {
            pendingSub = saved.subType.isEmpty() ? null : saved.subType;
            select(caseSpin, saved.caseType);
            select(courtSpin, saved.court);
            if (saved.year > 0) select(yearSpin, String.valueOf(saved.year));
            nextDate = saved.nextDate;
            lawyer.setText(saved.lawyer);
        }
        updateNextButton();

        caseSpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, android.view.View v, int pos, long id) {
                fillSubs();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        setContentView(sv);
    }

    private void fillSubs() {
        String ct = val(caseSpin);
        String[] subs = ct.isEmpty() ? new String[0] : DetailsStore.subsFor(ct);
        subSpin.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, withSelect(subs)));
        if (pendingSub != null) {
            select(subSpin, pendingSub);
            pendingSub = null;
        }
    }

    private String[] withSelect(String[] items) {
        String[] out = new String[items.length + 1];
        out[0] = SELECT;
        System.arraycopy(items, 0, out, 1, items.length);
        return out;
    }

    private Spinner spinner(String[] items) {
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, withSelect(items)));
        return s;
    }

    private String[] years() {
        int now = Calendar.getInstance().get(Calendar.YEAR);
        String[] out = new String[now - 1950 + 1];
        for (int y = now, i = 0; y >= 1950; y--, i++) out[i] = String.valueOf(y);
        return out;
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setPadding(0, 20, 0, 0);
        return t;
    }

    private void select(Spinner s, String value) {
        if (value == null || value.isEmpty()) return;
        for (int i = 0; i < s.getAdapter().getCount(); i++) {
            if (value.equals(String.valueOf(s.getAdapter().getItem(i)))) {
                s.setSelection(i);
                return;
            }
        }
    }

    private String val(Spinner s) {
        Object o = s.getSelectedItem();
        if (o == null || SELECT.equals(o)) return "";
        return o.toString();
    }

    private void updateNextButton() {
        nextBtn.setText(nextDate.isEmpty()
                ? "Pick next date"
                : "Next date: " + DetailsStore.formatDate(nextDate));
    }

    private void pickDate() {
        Calendar min = Calendar.getInstance();
        min.add(Calendar.DAY_OF_YEAR, 1);
        min.set(Calendar.HOUR_OF_DAY, 0);
        min.set(Calendar.MINUTE, 0);
        min.set(Calendar.SECOND, 0);
        min.set(Calendar.MILLISECOND, 0);

        Calendar init = (Calendar) min.clone();
        if (!nextDate.isEmpty()) {
            try {
                Date d = ISO.parse(nextDate);
                Calendar c = Calendar.getInstance();
                c.setTime(d);
                if (!c.before(min)) init = c;
            } catch (ParseException ignored) {
            }
        }

        DatePickerDialog dlg = new DatePickerDialog(this, (view, y, m, d) -> {
            nextDate = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
            updateNextButton();
        }, init.get(Calendar.YEAR), init.get(Calendar.MONTH), init.get(Calendar.DAY_OF_MONTH));
        dlg.getDatePicker().setMinDate(min.getTimeInMillis());
        dlg.show();
    }

    private void save() {
        DetailsStore.Details d = new DetailsStore.Details();
        d.caseType = val(caseSpin);
        d.subType = val(subSpin);
        d.court = val(courtSpin);
        String y = val(yearSpin);
        d.year = y.isEmpty() ? 0 : Integer.parseInt(y);
        d.nextDate = nextDate;
        d.lawyer = lawyer.getText().toString().trim();
        store.save(path, d);
        store.addLawyer(d.lawyer);
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
        finish();
    }
}
