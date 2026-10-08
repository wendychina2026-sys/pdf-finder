package com.example.twobuttons;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Edit case types, sub case types, sub-sub case types and courts from inside the app. */
public class OptionsEditorActivity extends BaseActivity {

    private static final int ROOT = 0, TYPES = 1, SUBS = 2, SUBSUBS = 3, COURTS = 4;

    private CaseOptions opts;
    private DetailsStore store;
    private ArrayAdapter<String> adapter;
    private TextView crumb;
    private Button addBtn;
    private int level = ROOT;
    private String curType = "";
    private String curSub = "";
    private final List<String> shown = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Edit dropdown lists");
        opts = new CaseOptions(this);
        store = new DetailsStore(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        crumb = new TextView(this);
        crumb.setTextSize(16);
        crumb.setPadding(0, 0, 0, 16);
        root.addView(crumb);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        ListView lv = new ListView(this);
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((p, v, pos, id) -> onItem(shown.get(pos)));
        root.addView(lv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addBtn = new Button(this);
        addBtn.setText("Add");
        addBtn.setOnClickListener(v -> onAdd());
        Button reset = new Button(this);
        reset.setText("Reset all to defaults");
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Reset all lists to defaults?")
                .setMessage("Your edits to all lists are lost.")
                .setPositiveButton("Reset", (d, w) -> {
                    opts.resetToDefaults();
                    level = ROOT;
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show());
        row.addView(addBtn, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(reset, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(row);

        setContentView(root);
        render();
    }

    private void render() {
        shown.clear();
        switch (level) {
            case ROOT:
                shown.add("Case types (with sub and sub-sub types)");
                shown.add("Courts");
                crumb.setText("Choose list to edit");
                break;
            case TYPES:
                shown.addAll(opts.types());
                crumb.setText("Case types");
                break;
            case SUBS:
                shown.addAll(opts.subs(curType));
                crumb.setText("Case types > " + curType);
                break;
            case SUBSUBS:
                shown.addAll(opts.subSubs(curType, curSub));
                crumb.setText(curType + " > " + curSub);
                break;
            default:
                shown.addAll(opts.courts());
                crumb.setText("Courts");
        }
        adapter.clear();
        adapter.addAll(shown);
        addBtn.setEnabled(level != ROOT);
    }

    private void onItem(String item) {
        if (level == ROOT) {
            level = shown.indexOf(item) == 0 ? TYPES : COURTS;
            render();
            return;
        }
        final boolean canOpen = level == TYPES || level == SUBS;
        final String[] choices = canOpen
                ? new String[]{"Open", "Rename", "Delete"}
                : new String[]{"Rename", "Delete"};
        new AlertDialog.Builder(this)
                .setTitle(item)
                .setItems(choices, (d, which) -> {
                    String c = choices[which];
                    if (c.equals("Open")) {
                        if (level == TYPES) curType = item; else curSub = item;
                        level++;
                        render();
                    } else if (c.equals("Rename")) {
                        ask("Rename", item, n -> {
                            String nn = n.trim();
                            if (rename(item, nn)) {
                                int moved = migrate(item, nn);
                                if (moved > 0) toast(moved + (moved == 1 ? " file updated" : " files updated"));
                            } else {
                                toast("Empty or duplicate name");
                            }
                            render();
                        });
                    } else {
                        confirmDelete(item);
                    }
                })
                .show();
    }

    private boolean rename(String old, String n) {
        switch (level) {
            case TYPES: return opts.renameType(old, n);
            case SUBS: return opts.renameSub(curType, old, n);
            case SUBSUBS: return opts.renameSubSub(curType, curSub, old, n);
            default: return opts.renameCourt(old, n);
        }
    }

    /** Update saved file details after a rename. Returns files changed. */
    private int migrate(String old, String n) {
        switch (level) {
            case TYPES: return store.renameType(old, n);
            case SUBS: return store.renameSub(curType, old, n);
            case SUBSUBS: return store.renameSubSub(curType, curSub, old, n);
            default: return store.renameCourt(old, n);
        }
    }

    private int usage(String item) {
        switch (level) {
            case TYPES: return store.usesType(item);
            case SUBS: return store.usesSub(curType, item);
            case SUBSUBS: return store.usesSubSub(curType, curSub, item);
            default: return store.usesCourt(item);
        }
    }

    private void confirmDelete(String item) {
        int used = usage(item);
        StringBuilder msg = new StringBuilder();
        if (used > 0) {
            msg.append(used).append(used == 1 ? " file uses" : " files use")
                    .append(" this. Saved text stays on them but will not show in the dropdown.\n\n");
        }
        if (level == TYPES || level == SUBS) msg.append("Everything under it is deleted too.");
        new AlertDialog.Builder(this)
                .setTitle("Delete \"" + item + "\"?")
                .setMessage(msg.length() == 0 ? null : msg.toString().trim())
                .setPositiveButton("Delete", (d, w) -> {
                    switch (level) {
                        case TYPES: opts.deleteType(item); break;
                        case SUBS: opts.deleteSub(curType, item); break;
                        case SUBSUBS: opts.deleteSubSub(curType, curSub, item); break;
                        default: opts.deleteCourt(item);
                    }
                    render();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void onAdd() {
        ask("Add new", "", n -> {
            boolean ok;
            switch (level) {
                case TYPES: ok = opts.addType(n); break;
                case SUBS: ok = opts.addSub(curType, n); break;
                case SUBSUBS: ok = opts.addSubSub(curType, curSub, n); break;
                default: ok = opts.addCourt(n);
            }
            if (!ok) toast("Empty or duplicate name");
            render();
        });
    }

    private void ask(String title, String initial, Consumer<String> done) {
        EditText e = new EditText(this);
        e.setText(initial);
        e.setSelection(initial.length());
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(e)
                .setPositiveButton("OK", (d, w) -> done.accept(e.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (level == ROOT) { super.onBackPressed(); return; }
        level = (level == SUBSUBS) ? SUBS : (level == SUBS) ? TYPES : ROOT;
        render();
    }
}
