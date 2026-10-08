package com.example.twobuttons;

import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Manage Court page. + adds, tap renames, trash deletes. */
public class ManageCourtActivity extends BaseActivity {

    private LinearLayout box;
    private DiaryStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Manage Court");
        store = new DiaryStore(this);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(AppTheme.bg(this));
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = LegalUi.dp(this, 16);
        box.setPadding(p, p, p, p);
        sv.addView(box);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        m.add(0, 1, 0, "+ Add court");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem it) {
        if (it.getItemId() == 1) {
            edit(-1);
            return true;
        }
        return super.onOptionsItemSelected(it);
    }

    private void render() {
        box.removeAllViews();
        List<String> courts = store.courts();
        if (courts.isEmpty()) box.addView(CaseViews.emptyNote(this, "No courts. Use menu > Add court."));
        for (int i = 0; i < courts.size(); i++) {
            final int idx = i;
            LinearLayout row = LegalUi.card(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView t = CaseViews.text(this, courts.get(i), 17, AppTheme.text(this), false);
            row.addView(t, LegalUi.weight());
            android.widget.ImageView del = CaseViews.icon(this, R.drawable.ic_delete, 0xFFF44336, 26);
            del.setPadding(LegalUi.dp(this, 6), LegalUi.dp(this, 6), LegalUi.dp(this, 6), LegalUi.dp(this, 6));
            del.setOnClickListener(v -> LegalUi.confirm(this, "Delete " + courts.get(idx) + "?",
                    "This cannot be undone.", () -> {
                        List<String> l = store.courts();
                        if (idx < l.size()) l.remove(idx);
                        store.saveCourts(l);
                        render();
                    }));
            row.addView(del);
            row.setOnClickListener(v -> edit(idx));
            box.addView(row, CaseViews.lp(this, 0, 10));
        }
    }

    private void edit(int idx) {
        List<String> l = store.courts();
        LinearLayout f = LegalUi.form(this);
        EditText name = LegalUi.input(this, f, "Court name", idx >= 0 ? l.get(idx) : "",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        LegalUi.formDialog(this, idx >= 0 ? "Rename court" : "Add court", f, () -> {
            String s = LegalUi.str(name);
            if (!LegalUi.need(this, s, "Court name")) return false;
            if (idx >= 0) l.set(idx, s); else l.add(s);
            store.saveCourts(l);
            render();
            return true;
        }, null);
    }
}
