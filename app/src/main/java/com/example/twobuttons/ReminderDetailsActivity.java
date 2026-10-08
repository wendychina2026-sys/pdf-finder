package com.example.twobuttons;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Reminder Details page. Edit / delete reminder, add new, switch between reminders. */
public class ReminderDetailsActivity extends BaseActivity {

    private static final int M_EDIT = 1, M_DELETE = 2, M_SWITCH = 3, M_ADD = 4;

    private LinearLayout box;
    private LegalStore store;
    private String curId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Reminder Details");
        store = new LegalStore(this);
        curId = getIntent().getStringExtra("id");

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(AppTheme.bg(this));
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = LegalUi.dp(this, 16);
        box.setPadding(p, p, p, p * 2);
        sv.addView(box);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private LegalStore.Reminder current() {
        LegalStore.Reminder r = store.reminder(curId);
        if (r == null) {
            List<LegalStore.Reminder> all = store.reminders();
            if (!all.isEmpty()) {
                r = all.get(0);
                curId = r.id;
            }
        }
        return r;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        m.add(0, M_EDIT, 0, "Edit reminder");
        m.add(0, M_DELETE, 1, "Delete reminder");
        m.add(0, M_SWITCH, 2, "Switch reminder");
        m.add(0, M_ADD, 3, "Add new reminder");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem it) {
        LegalStore.Reminder r = current();
        switch (it.getItemId()) {
            case M_EDIT:
                if (r != null) editReminder(r, false);
                return true;
            case M_DELETE:
                if (r != null) {
                    LegalUi.confirm(this, "Delete this reminder?", "This cannot be undone.", () -> {
                        store.deleteReminder(r.id);
                        curId = null;
                        render();
                    });
                }
                return true;
            case M_SWITCH:
                switchReminder();
                return true;
            case M_ADD:
                addReminder();
                return true;
            default:
                return super.onOptionsItemSelected(it);
        }
    }

    private void switchReminder() {
        List<LegalStore.Reminder> all = store.reminders();
        if (all.isEmpty()) {
            Toast.makeText(this, "No reminders yet", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[all.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = all.get(i).title + " - " + all.get(i).caseName;
        }
        new AlertDialog.Builder(this)
                .setTitle("Reminders")
                .setItems(names, (d, which) -> {
                    curId = all.get(which).id;
                    render();
                })
                .show();
    }

    private void addReminder() {
        LegalStore.Reminder r = new LegalStore.Reminder();
        r.title = "Hearing Reminder";
        r.status = "PENDING";
        r.sentOn = new SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.US).format(new Date());
        editReminder(r, true);
    }

    private void render() {
        box.removeAllViews();
        LegalStore.Reminder r = current();
        if (r == null) {
            box.addView(CaseViews.emptyNote(this, "No reminders"));
            Button add = LegalUi.outlineButton(this, "Add reminder");
            add.setOnClickListener(v -> addReminder());
            box.addView(add, CaseViews.lp(this, 12, 0));
            return;
        }

        // header card
        LinearLayout head = LegalUi.card(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout ic = new LinearLayout(this);
        ic.setGravity(Gravity.CENTER);
        ic.setBackground(CaseViews.box(this, AppTheme.isDark(this) ? 0xFF2A2C44 : 0xFFDCEBFA, 28, 0, 0));
        ic.addView(CaseViews.icon(this, R.drawable.ic_bell, AppTheme.info(this), 28));
        head.addView(ic, new LinearLayout.LayoutParams(LegalUi.dp(this, 56), LegalUi.dp(this, 56)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(LegalUi.dp(this, 16), 0, 0, 0);
        col.addView(CaseViews.text(this, r.title, 17, AppTheme.text(this), true));
        col.addView(CaseViews.text(this, "Case: " + r.caseName, 14, AppTheme.sub(this), false));
        head.addView(col, LegalUi.weight());
        box.addView(head);

        // channel + status chip
        LinearLayout ch = new LinearLayout(this);
        ch.setOrientation(LinearLayout.HORIZONTAL);
        ch.setGravity(Gravity.CENTER_VERTICAL);
        ch.setPadding(0, LegalUi.dp(this, 18), 0, 0);
        LinearLayout chCol = new LinearLayout(this);
        chCol.setOrientation(LinearLayout.VERTICAL);
        chCol.addView(CaseViews.text(this, "Channel", 13, AppTheme.sub(this), false));
        chCol.addView(CaseViews.text(this, r.channel, 18, AppTheme.text(this), false));
        ch.addView(chCol, LegalUi.weight());
        boolean ok = "DELIVERED".equalsIgnoreCase(r.status);
        boolean bad = "FAILED".equalsIgnoreCase(r.status);
        boolean dark = AppTheme.isDark(this);
        int fill = bad ? (dark ? 0xFF3D2020 : 0xFFFDE0E0) : (dark ? 0xFF1F2F44 : 0xFFDCEBFA);
        int txt = bad ? LegalUi.RED : AppTheme.info(this);
        ch.addView(LegalUi.chip(this, (ok ? "\u2713\u2713 " : "") + r.status, fill, txt));
        box.addView(ch);

        // sent on
        TextView so = CaseViews.text(this, "Sent On", 13, AppTheme.sub(this), false);
        so.setPadding(0, LegalUi.dp(this, 16), 0, 0);
        box.addView(so);
        box.addView(CaseViews.text(this, r.sentOn, 18, AppTheme.text(this), false));

        // message
        TextView mh = CaseViews.text(this, "Message Content", 15, AppTheme.text(this), true);
        mh.setPadding(0, LegalUi.dp(this, 18), 0, LegalUi.dp(this, 8));
        box.addView(mh);
        LinearLayout msg = new LinearLayout(this);
        msg.setPadding(LegalUi.dp(this, 16), LegalUi.dp(this, 16), LegalUi.dp(this, 16), LegalUi.dp(this, 16));
        msg.setBackground(CaseViews.box(this, AppTheme.field(this), 10, AppTheme.fieldStroke(this), 1));
        TextView mt = CaseViews.text(this, r.message, 15, AppTheme.text(this), false);
        mt.setTextIsSelectable(true);
        mt.setLineSpacing(0, 1.15f);
        msg.addView(mt);
        box.addView(msg);

        // delivery box
        LinearLayout res = new LinearLayout(this);
        res.setOrientation(LinearLayout.VERTICAL);
        res.setPadding(LegalUi.dp(this, 16), LegalUi.dp(this, 14), LegalUi.dp(this, 16), LegalUi.dp(this, 14));
        int boxFill, boxStroke, titleColor;
        String head1, head2;
        if (ok) {
            boxFill = dark ? 0xFF16291B : 0xFFE8F5E9; boxStroke = dark ? 0xFF2E5A37 : 0xFFA5D6A7;
            titleColor = dark ? 0xFF81C784 : 0xFF1B5E20;
            head1 = "Delivered Successfully";
            head2 = "Recipient received this message via " + r.channel;
        } else if (bad) {
            boxFill = dark ? 0xFF3D2020 : 0xFFFDECEA; boxStroke = dark ? 0xFF6B3030 : 0xFFEF9A9A;
            titleColor = LegalUi.RED;
            head1 = "Delivery Failed";
            head2 = "Message could not be sent via " + r.channel;
        } else {
            boxFill = AppTheme.card(this); boxStroke = AppTheme.cardStroke(this);
            titleColor = AppTheme.text(this);
            head1 = r.status.isEmpty() ? "Not sent" : capital(r.status);
            head2 = "Channel: " + r.channel;
        }
        res.setBackground(CaseViews.box(this, boxFill, 10, boxStroke, 1));
        res.addView(CaseViews.text(this, head1, 15, titleColor, true));
        res.addView(CaseViews.text(this, head2, 13, titleColor, false));
        box.addView(res, CaseViews.lp(this, 16, 0));
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.US) + s.substring(1).toLowerCase(Locale.US);
    }

    private void editReminder(LegalStore.Reminder r, boolean isNew) {
        LinearLayout f = LegalUi.form(this);
        EditText title = LegalUi.text(this, f, "Title", r.title);
        EditText cs = LegalUi.text(this, f, "Case", r.caseName);
        Spinner channel = LegalUi.spinner(this, f, "Channel", LegalStore.CHANNELS, r.channel);
        Spinner status = LegalUi.spinner(this, f, "Status", LegalStore.REM_STATUS, r.status);
        EditText sent = LegalUi.text(this, f, "Sent on", r.sentOn);
        EditText msg = LegalUi.multiline(this, f, "Message content", r.message);

        LegalUi.formDialog(this, isNew ? "Add reminder" : "Edit reminder", f, () -> {
            if (!LegalUi.need(this, LegalUi.str(title), "Title")) return false;
            r.title = LegalUi.str(title);
            r.caseName = LegalUi.str(cs);
            r.channel = (String) channel.getSelectedItem();
            r.status = (String) status.getSelectedItem();
            r.sentOn = LegalUi.str(sent);
            r.message = msg.getText().toString();
            store.saveReminder(r);
            curId = r.id;
            render();
            return true;
        }, null);
    }
}
