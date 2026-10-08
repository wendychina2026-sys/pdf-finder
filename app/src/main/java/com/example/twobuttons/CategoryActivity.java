package com.example.twobuttons;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;

public class CategoryActivity extends BaseActivity {

    private CaseStore store;
    private ArrayAdapter<CaseEntry> adapter;
    private ListView list;
    private Spinner typeSpinner;
    private EditText titleEdit;
    private EditText numberEdit;
    private Button updateButton;
    private Button deleteButton;
    private CaseEntry selected = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new CaseStore(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);

        // ----- MAIN -----
        root.addView(header("MAIN"));

        typeSpinner = new Spinner(this);
        typeSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, CaseStore.TYPES));
        root.addView(label("Type of case"));
        root.addView(typeSpinner);

        titleEdit = new EditText(this);
        titleEdit.setHint("Title of case");
        titleEdit.setSingleLine(true);
        root.addView(label("Title of case"));
        root.addView(titleEdit);

        numberEdit = new EditText(this);
        numberEdit.setHint("Case number");
        numberEdit.setSingleLine(true);
        root.addView(label("Case number"));
        root.addView(numberEdit);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button addButton = new Button(this);
        addButton.setText("Add new");
        addButton.setOnClickListener(v -> onAdd());
        updateButton = new Button(this);
        updateButton.setText("Update");
        updateButton.setOnClickListener(v -> onUpdate());
        deleteButton = new Button(this);
        deleteButton.setText("Delete");
        deleteButton.setOnClickListener(v -> onDelete());
        for (Button b : new Button[]{addButton, updateButton, deleteButton}) {
            buttons.addView(b, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        root.addView(buttons);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_single_choice);
        list = new ListView(this);
        list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) -> {
            selected = adapter.getItem(pos);
            fillForm(selected);
            updateButtons();
        });
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // ----- SUB MAIN (placeholder) -----
        root.addView(header("SUB MAIN"));
        TextView soon = new TextView(this);
        soon.setText("Coming next.");
        root.addView(soon);

        setContentView(root);
        reload();
    }

    private TextView header(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(18);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(0, 24, 0, 8);
        return t;
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setPadding(0, 12, 0, 0);
        return t;
    }

    private void reload() {
        adapter.clear();
        adapter.addAll(store.load());
        selected = null;
        list.clearChoices();
        updateButtons();
    }

    private void updateButtons() {
        updateButton.setEnabled(selected != null);
        deleteButton.setEnabled(selected != null);
    }

    private void fillForm(CaseEntry e) {
        int idx = Arrays.asList(CaseStore.TYPES).indexOf(e.type);
        typeSpinner.setSelection(Math.max(idx, 0));
        titleEdit.setText(e.title);
        numberEdit.setText(e.number);
    }

    private void clearForm() {
        typeSpinner.setSelection(0);
        titleEdit.setText("");
        numberEdit.setText("");
    }

    private boolean titleOk() {
        if (titleEdit.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Enter title of case", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void onAdd() {
        if (!titleOk()) return;
        store.add((String) typeSpinner.getSelectedItem(),
                titleEdit.getText().toString().trim(),
                numberEdit.getText().toString().trim());
        clearForm();
        reload();
    }

    private void onUpdate() {
        if (selected == null || !titleOk()) return;
        selected.type = (String) typeSpinner.getSelectedItem();
        selected.title = titleEdit.getText().toString().trim();
        selected.number = numberEdit.getText().toString().trim();
        store.update(selected);
        clearForm();
        reload();
    }

    private void onDelete() {
        if (selected == null) return;
        String id = selected.id;
        new AlertDialog.Builder(this)
                .setTitle("Delete this case?")
                .setMessage(selected.title)
                .setPositiveButton("Delete", (d, w) -> {
                    store.delete(id);
                    clearForm();
                    reload();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
