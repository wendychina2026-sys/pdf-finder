package com.example.twobuttons;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * "Attach to" for the Edit document dialogs, all dropdowns:
 *  1) Attach to: General Document Vault / Case / Client
 *  2) appears only for Case or Client: the saved cases, or the saved clients, to pick from
 *  3) Stage appears only when a Case is picked (a client or the Vault has no hearing stage)
 */
final class MatterPicker {
    static final String VAULT = "General Document Vault";
    private static final String[] KINDS = {VAULT, "Case", "Client"};

    private final Activity a;
    private final List<String> cases = new ArrayList<>();
    private final List<String> clients = new ArrayList<>();
    private String kind = VAULT;
    private String target = "";
    private final TextView kindTv, targetTv;
    private final LinearLayout targetBox, stageBox;
    private final Spinner stage;

    MatterPicker(Activity a, LinearLayout f, String matter, String stageNow) {
        this.a = a;
        for (DiaryStore.HCase hc : new DiaryStore(a).cases()) cases.add(hc.title());
        for (LegalStore.Client c : new LegalStore(a).clients()) clients.add(c.name);

        // work out what the saved text refers to
        String m = matter == null ? "" : matter.trim();
        if (m.isEmpty() || m.equals(VAULT)) {
            kind = VAULT;
        } else if (clients.contains(m) && !cases.contains(m)) {
            kind = "Client";
            target = m;
        } else {
            kind = "Case";
            target = m;
            if (!cases.contains(m)) cases.add(0, m);   // older free-text value stays selectable
        }

        LegalUi.label(a, f, "Attach to");
        kindTv = FieldStyle.dropdown(a, f, VAULT, v -> {
            List<String> items = Arrays.asList(KINDS);
            PickerDialogs.optionSheet(a, "Attach to", null, items, items.indexOf(kind), i -> {
                String k = items.get(i);
                if (!k.equals(kind)) target = "";
                kind = k;
                refresh();
            }), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        targetBox = new LinearLayout(a);
        targetBox.setOrientation(LinearLayout.VERTICAL);
        f.addView(targetBox);
        LegalUi.label(a, targetBox, "Select");
        targetTv = FieldStyle.dropdown(a, targetBox, "Select", v -> {
            final boolean isCase = kind.equals("Case");
            List<String> items = isCase ? cases : clients;
            if (items.isEmpty()) {
                android.widget.Toast.makeText(a, isCase ? "No saved cases yet" : "No saved clients yet",
                        android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            PickerDialogs.optionSheet(a, isCase ? "Saved cases" : "Saved clients", null, items,
                    items.indexOf(target), i -> {
                        target = items.get(i);
                        refresh();
                    });
        }, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        stageBox = new LinearLayout(a);
        stageBox.setOrientation(LinearLayout.VERTICAL);
        f.addView(stageBox);
        List<String> st = new ArrayList<>();
        st.add("");
        st.addAll(new StageStore(a).list());
        if (stageNow != null && !stageNow.isEmpty() && !st.contains(stageNow)) st.add(stageNow);
        stage = LegalUi.spinner(a, stageBox, "Stage", st.toArray(new String[0]), stageNow);
        refresh();
    }

    private void refresh() {
        FieldStyle.setDropdownValue(kindTv, kind, VAULT);
        boolean pickable = !kind.equals(VAULT);
        targetBox.setVisibility(pickable ? View.VISIBLE : View.GONE);
        FieldStyle.setDropdownValue(targetTv, target,
                kind.equals("Case") ? "Select case" : "Select client");
        stageBox.setVisibility(kind.equals("Case") && !target.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** Text saved on the document: the Vault, or the chosen case / client name. */
    String matter() {
        return kind.equals(VAULT) || target.isEmpty() ? VAULT : target;
    }

    /** Hearing stage; only meaningful for a case. */
    String stage() {
        if (!kind.equals("Case") || target.isEmpty()) return "";
        Object s = stage.getSelectedItem();
        return s == null ? "" : (String) s;
    }

    /** False (and a toast) when Case / Client was chosen but no name picked. */
    boolean valid() {
        if (!kind.equals(VAULT) && target.isEmpty()) {
            android.widget.Toast.makeText(a, "Select a " + kind.toLowerCase() + " to attach to",
                    android.widget.Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
}
