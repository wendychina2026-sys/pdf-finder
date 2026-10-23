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
 * Courts; District Court opens State, then District, then Court complex (Delhi fixed, other states
 * fetched from eCourts and cached). The saved value is the path, e.g.
 * "District Court > Delhi > Central > Tis Hazari Courts". Each sub-level shows the path so far as
 * its subtitle; the cross goes up exactly one level (the first sub-level returns to the court list).
 */
final class CourtPicker {
    private CourtPicker() {}

    static final String SEP = CategoryPicker.SEP;

    /** First level of a saved path ("High Court" from "High Court > Delhi High Court"). */
    static String base(String path) {
        return path == null ? "" : path.split(SEP)[0];
    }

    /** back: reopens the court list (what the cross on the first sub-level does). */
    static void pick(Activity a, String kind, Runnable back, Consumer<String> onDone) {
        if ("High Court".equals(kind)) {
            List<String> l = Arrays.asList(CourtDirectory.HIGH_COURTS);
            PickerDialogs.optionSheet(a, "Choose High Court", kind, l, -1,
                    i -> onDone.accept(kind + SEP + l.get(i)), back);
        } else if ("District Court".equals(kind)) {
            state(a, kind, back, onDone);
        } else {
            onDone.accept(kind);
        }
    }

    private static void state(Activity a, String path, Runnable back, Consumer<String> onDone) {
        Runnable self = () -> state(a, path, back, onDone);
        show(a, "State", path, CourtDirectory.offlineStates(), back, it -> {
            String p = path + SEP + it.name;
            if (CourtDirectory.isDelhi(it.name)) {         // fixed list, no network
                delhiDistrict(a, p, self, onDone);
                return;
            }
            CourtDirectory.states(a, live -> {             // need eCourts state code
                String code = "";
                for (CourtDirectory.Item x : live) {
                    if (x.name.equalsIgnoreCase(it.name)) code = x.code;
                }
                if (code.isEmpty()) {
                    typeChain(a, p, self, onDone, "No internet for district list.");
                } else {
                    district(a, p, code, self, onDone);
                }
            });
        });
    }

    private static void delhiDistrict(Activity a, String path, Runnable up, Consumer<String> onDone) {
        Runnable self = () -> delhiDistrict(a, path, up, onDone);
        show(a, "District", path, CourtDirectory.delhiDistricts(), up, d ->
                show(a, "Court complex", path + SEP + d.name, CourtDirectory.delhiComplexes(d.name),
                        self, c -> onDone.accept(path + SEP + d.name + SEP + c.name)));
    }

    private static void district(Activity a, String path, String stateCode, Runnable up,
                                 Consumer<String> onDone) {
        Runnable self = () -> district(a, path, stateCode, up, onDone);
        CourtDirectory.districts(a, stateCode,
                list -> show(a, "District", path, list, up, it ->
                        complex(a, path + SEP + it.name, stateCode, it.code, self, onDone)),
                err -> typeChain(a, path, up, onDone, err));
    }

    private static void complex(Activity a, String path, String stateCode, String distCode,
                                Runnable up, Consumer<String> onDone) {
        CourtDirectory.complexes(a, stateCode, distCode,
                list -> show(a, "Court complex", path, list, up, it -> onDone.accept(path + SEP + it.name)),
                err -> typeIt(a, "Court complex", err, up, c -> onDone.accept(path + SEP + c)));
    }

    private static void typeChain(Activity a, String path, Runnable up, Consumer<String> onDone,
                                  String why) {
        Runnable self = () -> typeChain(a, path, up, onDone, why);
        typeIt(a, "District", why, up, s ->
                typeIt(a, "Court complex", "Type it.", self, c ->
                        onDone.accept(path + SEP + s + SEP + c)));
    }

    private static void show(Activity a, String title, String parent, List<CourtDirectory.Item> items,
                             Runnable up, Consumer<CourtDirectory.Item> cb) {
        List<String> names = new ArrayList<>();
        for (CourtDirectory.Item it : items) names.add(it.name);
        PickerDialogs.optionSheet(a, title, parent, names, -1, i -> cb.accept(items.get(i)), up);
    }

    private static void typeIt(Activity a, String what, String why, Runnable up, Consumer<String> cb) {
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
                .setNegativeButton("Back", (d, w) -> up.run())
                .setOnCancelListener(d -> up.run())
                .show();
    }
}
