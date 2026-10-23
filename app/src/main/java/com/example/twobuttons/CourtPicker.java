package com.example.twobuttons;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Court drill-down, same idea as CategoryPicker. Pick a court; High Court opens the list of High
 * Courts; District Court opens State, then District, then Court complex (fetched from eCourts and
 * cached). The saved value is the path, e.g. "District Court › Delhi › Central › Tis Hazari Court Complex".
 */
final class CourtPicker {
    private CourtPicker() {}

    static final String SEP = CategoryPicker.SEP;

    /** First level of a saved path ("High Court" from "High Court › Delhi High Court"). */
    static String base(String path) {
        return path == null ? "" : path.split(SEP)[0];
    }

    static void pick(Activity a, String kind, Consumer<String> onDone) {
        if ("High Court".equals(kind)) {
            List<String> l = Arrays.asList(CourtDirectory.HIGH_COURTS);
            PickerDialogs.optionSheet(a, "High Court", "Choose one", l, -1,
                    i -> onDone.accept(kind + SEP + l.get(i)));
        } else if ("District Court".equals(kind)) {
            pickState(a, kind, onDone);
        } else {
            onDone.accept(kind);
        }
    }

    private static void pickState(Activity a, String path, Consumer<String> onDone) {
        show(a, "State", CourtDirectory.offlineStates(), it -> {
            String p = path + SEP + it.name;
            if (CourtDirectory.isDelhi(it.name)) {         // fixed list, no network
                show(a, "District", CourtDirectory.delhiDistricts(), d ->
                        show(a, "Court complex", CourtDirectory.delhiComplexes(d.name), c ->
                                onDone.accept(p + SEP + d.name + SEP + c.name)));
                return;
            }
            CourtDirectory.states(a, live -> {             // need eCourts state code
                String code = "";
                for (CourtDirectory.Item x : live) {
                    if (x.name.equalsIgnoreCase(it.name)) code = x.code;
                }
                if (code.isEmpty()) {
                    typeIt(a, "District", "No internet for district list.", s ->
                            typeIt(a, "Court complex", "Type it.", c ->
                                    onDone.accept(p + SEP + s + SEP + c)));
                } else {
                    pickDistrict(a, p, code, onDone);
                }
            });
        });
    }

    private static void pickDistrict(Activity a, String path, String stateCode, Consumer<String> onDone) {
        CourtDirectory.districts(a, stateCode,
                list -> show(a, "District", list, it ->
                        pickComplex(a, path + SEP + it.name, stateCode, it.code, onDone)),
                err -> typeIt(a, "District", err, s ->
                        typeIt(a, "Court complex", "Type it.", c ->
                                onDone.accept(path + SEP + s + SEP + c))));
    }

    private static void pickComplex(Activity a, String path, String stateCode, String distCode,
                                    Consumer<String> onDone) {
        CourtDirectory.complexes(a, stateCode, distCode,
                list -> show(a, "Court complex", list, it -> onDone.accept(path + SEP + it.name)),
                err -> typeIt(a, "Court complex", err, c -> onDone.accept(path + SEP + c)));
    }

    private static void show(Activity a, String title, List<CourtDirectory.Item> items,
                             Consumer<CourtDirectory.Item> cb) {
        List<String> names = new ArrayList<>();
        for (CourtDirectory.Item it : items) names.add(it.name);
        PickerDialogs.optionSheet(a, title, "Choose one", names, -1, i -> cb.accept(items.get(i)));
    }

    private static void typeIt(Activity a, String what, String why, Consumer<String> cb) {
        EditText e = new EditText(a);
        FieldStyle.hint(e, what);
        e.setSingleLine(true);
        new AlertDialog.Builder(a)
                .setTitle(what)
                .setMessage(why + " Type it instead.")
                .setView(e)
                .setPositiveButton("OK", (d, w) -> {
                    String s = e.getText().toString().trim();
                    if (!s.isEmpty()) cb.accept(s);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
