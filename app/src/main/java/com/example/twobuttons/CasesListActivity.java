package com.example.twobuttons;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.List;

/** "View All" page. mode = "upcoming" or "today". */
public class CasesListActivity extends Activity {

    private LinearLayout box;
    private boolean todayMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        todayMode = "today".equals(getIntent().getStringExtra("mode"));
        setTitle(todayMode ? "Today's Schedule" : "Upcoming Cases");

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.WHITE);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = CaseViews.dp(this, 16);
        box.setPadding(p, p, p, p);
        sv.addView(box);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        box.removeAllViews();
        List<CaseItems.Item> items = todayMode ? CaseItems.today(this) : CaseItems.upcoming(this);
        if (items.isEmpty()) {
            box.addView(CaseViews.emptyNote(this,
                    todayMode ? "Nothing scheduled today" : "No upcoming cases"));
            return;
        }
        for (CaseItems.Item it : items) {
            box.addView(CaseViews.caseRow(this, it,
                    v -> CaseViews.openDetails(this, it.path)), CaseViews.lp(this, 0, 10));
        }
    }
}
