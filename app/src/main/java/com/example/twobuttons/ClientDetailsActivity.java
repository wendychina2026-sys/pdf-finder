package com.example.twobuttons;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Client Details page. Edit / delete client, add / edit / delete linked cases. */
public class ClientDetailsActivity extends BaseActivity {

    private static final int M_EDIT = 1, M_DELETE = 2, M_SWITCH = 3, M_ADD = 4;

    private LinearLayout box;
    private LegalStore store;
    private String curId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Client Details");
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

    private LegalStore.Client current() {
        LegalStore.Client c = store.client(curId);
        if (c == null) {
            List<LegalStore.Client> all = store.clients();
            if (!all.isEmpty()) {
                c = all.get(0);
                curId = c.id;
            }
        }
        return c;
    }

    // ---------- menu ----------

    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        m.add(0, M_EDIT, 0, "Edit client");
        m.add(0, M_DELETE, 1, "Delete client");
        m.add(0, M_SWITCH, 2, "Switch client");
        m.add(0, M_ADD, 3, "Add new client");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem it) {
        LegalStore.Client c = current();
        switch (it.getItemId()) {
            case M_EDIT:
                if (c != null) editClient(c, false);
                return true;
            case M_DELETE:
                if (c != null) {
                    LegalUi.confirm(this, "Delete " + c.name + "?",
                            "Client and linked cases are removed.", () -> {
                                store.deleteClient(c.id);
                                curId = null;
                                render();
                            });
                }
                return true;
            case M_SWITCH:
                switchClient();
                return true;
            case M_ADD:
                addClient();
                return true;
            default:
                return super.onOptionsItemSelected(it);
        }
    }

    private void switchClient() {
        List<LegalStore.Client> all = store.clients();
        if (all.isEmpty()) {
            Toast.makeText(this, "No clients yet", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[all.size()];
        for (int i = 0; i < names.length; i++) names[i] = all.get(i).name + "  (" + all.get(i).code + ")";
        new AlertDialog.Builder(this)
                .setTitle("Clients")
                .setItems(names, (d, which) -> {
                    curId = all.get(which).id;
                    render();
                })
                .show();
    }

    private void addClient() {
        LegalStore.Client c = new LegalStore.Client();
        c.code = store.nextClientCode();
        editClient(c, true);
    }

    // ---------- page ----------

    private void render() {
        box.removeAllViews();
        LegalStore.Client c = current();
        if (c == null) {
            box.addView(CaseViews.emptyNote(this, "No clients"));
            Button add = LegalUi.outlineButton(this, "Add client");
            add.setOnClickListener(v -> addClient());
            box.addView(add, CaseViews.lp(this, 12, 0));
            return;
        }

        // header card: avatar, name, id, Call / SMS
        LinearLayout head = LegalUi.card(this);
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        String ini = c.name.isEmpty() ? "?" : c.name.substring(0, 1).toUpperCase();
        TextView circle = CaseViews.text(this, ini, 26, AppTheme.accent(this), true);
        circle.setGravity(Gravity.CENTER);
        circle.setBackground(CaseViews.box(this, AppTheme.isDark(this) ? 0xFF2A2C44 : 0xFFD6ECFA, 36, 0, 0));
        top.addView(circle, new LinearLayout.LayoutParams(LegalUi.dp(this, 72), LegalUi.dp(this, 72)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(LegalUi.dp(this, 16), 0, 0, 0);
        names.addView(CaseViews.text(this, c.name, 20, AppTheme.text(this), true));
        names.addView(CaseViews.text(this, "Client ID: " + c.code, 14, AppTheme.sub(this), false));
        top.addView(names, LegalUi.weight());
        head.addView(top);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        Button call = LegalUi.outlineButton(this, "Call");
        call.setOnClickListener(v -> dial(Intent.ACTION_DIAL, "tel:", c.mobile));
        Button sms = LegalUi.outlineButton(this, "SMS");
        sms.setOnClickListener(v -> dial(Intent.ACTION_SENDTO, "smsto:", c.mobile));
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, LegalUi.dp(this, 48), 1f);
        l1.rightMargin = LegalUi.dp(this, 6);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, LegalUi.dp(this, 48), 1f);
        l2.leftMargin = LegalUi.dp(this, 6);
        btns.addView(call, l1);
        btns.addView(sms, l2);
        head.addView(btns, CaseViews.lp(this, 14, 0));
        box.addView(head);

        // contact & reference
        box.addView(LegalUi.sectionTitle(this, "Contact & Reference"));
        LinearLayout contact = LegalUi.card(this);
        contact.addView(infoRow("Mobile", c.mobile));
        contact.addView(infoRow("Address", c.address));
        contact.addView(infoRow("Reference", c.reference));
        box.addView(contact);

        // financial summary
        box.addView(LegalUi.sectionTitle(this, "Financial Summary"));
        LinearLayout fin = LegalUi.card(this);
        fin.setOrientation(LinearLayout.HORIZONTAL);
        fin.addView(money(LegalUi.money(c.total), "Total", AppTheme.text(this)), LegalUi.weight());
        fin.addView(money(LegalUi.money(c.paid), "Paid", AppTheme.text(this)), LegalUi.weight());
        fin.addView(money(LegalUi.money(c.due()), "Due", LegalUi.RED), LegalUi.weight());
        box.addView(fin);

        // documents
        box.addView(LegalUi.sectionTitle(this, "Documents"));
        LinearLayout docs = LegalUi.card(this);
        docs.setOrientation(LinearLayout.HORIZONTAL);
        docs.setGravity(Gravity.CENTER_VERTICAL);
        docs.addView(CaseViews.icon(this, R.drawable.ic_pdf, AppTheme.accent(this), 26));
        TextView dn = CaseViews.text(this, c.docsNote, 14, AppTheme.text(this), false);
        dn.setPadding(LegalUi.dp(this, 14), 0, LegalUi.dp(this, 8), 0);
        docs.addView(dn, LegalUi.weight());
        docs.addView(CaseViews.text(this, ">", 20, AppTheme.accent(this), true));
        docs.setOnClickListener(v -> startActivity(new Intent(this, DocumentsActivity.class)));
        box.addView(docs);

        // cases
        LinearLayout ch = new LinearLayout(this);
        ch.setOrientation(LinearLayout.HORIZONTAL);
        ch.setGravity(Gravity.CENTER_VERTICAL);
        ch.setPadding(0, LegalUi.dp(this, 20), 0, LegalUi.dp(this, 2));
        ch.addView(CaseViews.text(this, "Cases", 18, AppTheme.text(this), true), LegalUi.weight());
        TextView addCase = CaseViews.text(this, "Add Case", 14, AppTheme.accent(this), true);
        addCase.setPadding(LegalUi.dp(this, 8), LegalUi.dp(this, 4), 0, LegalUi.dp(this, 4));
        addCase.setOnClickListener(v -> editCase(c, null));
        ch.addView(addCase);
        box.addView(ch);
        TextView cnt = CaseViews.text(this, c.cases.size() + " linked case" + (c.cases.size() == 1 ? "" : "s"),
                13, AppTheme.sub(this), false);
        cnt.setPadding(0, 0, 0, LegalUi.dp(this, 8));
        box.addView(cnt);

        if (c.cases.isEmpty()) {
            box.addView(CaseViews.emptyNote(this, "No linked cases"));
        }
        for (LegalStore.CaseLink cl : c.cases) {
            box.addView(caseRow(c, cl), CaseViews.lp(this, 0, 10));
        }
    }

    private void dial(String action, String scheme, String number) {
        if (number == null || number.trim().isEmpty()) {
            Toast.makeText(this, "No mobile number", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(action, Uri.parse(scheme + number.replaceAll("\\s", ""))));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app to handle this", Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout infoRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, LegalUi.dp(this, 7), 0, LegalUi.dp(this, 7));
        TextView l = CaseViews.text(this, label, 14, AppTheme.sub(this), false);
        row.addView(l, new LinearLayout.LayoutParams(LegalUi.dp(this, 90), LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(CaseViews.text(this, value.isEmpty() ? "-" : value, 15, AppTheme.text(this), false), LegalUi.weight());
        return row;
    }

    private LinearLayout money(String value, String label, int color) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.addView(CaseViews.text(this, value, 18, color, true));
        col.addView(CaseViews.text(this, label, 13, AppTheme.sub(this), false));
        return col;
    }

    private LinearLayout caseRow(LegalStore.Client c, LegalStore.CaseLink cl) {
        LinearLayout row = LegalUi.card(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(CaseViews.icon(this, R.drawable.ic_court, AppTheme.accent(this), 26));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(LegalUi.dp(this, 14), 0, LegalUi.dp(this, 8), 0);
        col.addView(CaseViews.text(this, cl.title, 15, AppTheme.text(this), true));
        col.addView(CaseViews.text(this, cl.subtitle, 13, AppTheme.sub(this), false));
        col.addView(CaseViews.text(this, cl.date, 13, AppTheme.sub(this), false));
        row.addView(col, LegalUi.weight());
        boolean active = "Active".equalsIgnoreCase(cl.status);
        boolean dark = AppTheme.isDark(this);
        int fill = active ? (dark ? 0xFF1F3A25 : 0xFFD8F3DC) : (dark ? 0xFF2A2C44 : 0xFFDCEBFA);
        int txt = active ? (dark ? 0xFF81C784 : 0xFF2E7D32) : AppTheme.accent(this);
        row.addView(LegalUi.chip(this, cl.status, fill, txt));
        row.setOnClickListener(v -> editCase(c, cl));
        return row;
    }

    // ---------- dialogs ----------

    private void editClient(LegalStore.Client c, boolean isNew) {
        LinearLayout f = LegalUi.form(this);
        EditText code = LegalUi.text(this, f, "Client ID", c.code);
        EditText name = LegalUi.text(this, f, "Name", c.name);
        EditText mobile = LegalUi.input(this, f, "Mobile", c.mobile, InputType.TYPE_CLASS_PHONE);
        EditText addr = LegalUi.text(this, f, "Address", c.address);
        EditText ref = LegalUi.text(this, f, "Reference", c.reference);
        EditText total = LegalUi.input(this, f, "Total fee", String.valueOf(c.total), InputType.TYPE_CLASS_NUMBER);
        EditText paid = LegalUi.input(this, f, "Paid", String.valueOf(c.paid), InputType.TYPE_CLASS_NUMBER);
        EditText docs = LegalUi.text(this, f, "Documents note", c.docsNote);

        LegalUi.formDialog(this, isNew ? "Add client" : "Edit client", f, () -> {
            if (!LegalUi.need(this, LegalUi.str(name), "Name")) return false;
            c.code = LegalUi.str(code);
            c.name = LegalUi.str(name);
            c.mobile = LegalUi.str(mobile);
            c.address = LegalUi.str(addr);
            c.reference = LegalUi.str(ref);
            c.total = LegalUi.num(total);
            c.paid = LegalUi.num(paid);
            c.docsNote = LegalUi.str(docs);
            store.saveClient(c);
            curId = c.id;
            render();
            return true;
        }, null);
    }

    private void editCase(LegalStore.Client c, LegalStore.CaseLink existing) {
        LegalStore.CaseLink cl = existing != null ? existing : new LegalStore.CaseLink();
        LinearLayout f = LegalUi.form(this);
        EditText title = LegalUi.text(this, f, "Case title", cl.title);
        EditText sub = LegalUi.text(this, f, "Case type / number", cl.subtitle);
        EditText date = LegalUi.dateInput(this, f, "Date", cl.date);
        Spinner status = LegalUi.spinner(this, f, "Status", LegalStore.CASE_STATUS, cl.status);

        LegalUi.formDialog(this, existing == null ? "Add case" : "Edit case", f, () -> {
            if (!LegalUi.need(this, LegalUi.str(title), "Case title")) return false;
            cl.title = LegalUi.str(title);
            cl.subtitle = LegalUi.str(sub);
            cl.date = LegalUi.str(date);
            cl.status = (String) status.getSelectedItem();
            if (existing == null) c.cases.add(cl);
            store.saveClient(c);
            render();
            return true;
        }, existing == null ? null : () -> {
            c.cases.remove(existing);
            store.saveClient(c);
            render();
        });
    }
}
