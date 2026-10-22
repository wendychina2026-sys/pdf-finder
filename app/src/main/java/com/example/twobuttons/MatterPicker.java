package com.example.twobuttons;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * "Attach to Matter" for the Edit document dialogs. Same as the upload dialog:
 * one dropdown (General Document Vault, then the saved cases / clients), and the
 * Stage dropdown shows as soon as something other than the Vault is picked.
 */
final class MatterPicker {
    static final String VAULT = "-- General Document Vault --";
    private static final String VAULT_SAVED = "General Document Vault";

    private final List<String> items = new ArrayList<>();
    private String matter;
    private String stage;
    private final TextView matterTv, stageTv;
    private final LinearLayout stageBox;

    MatterPicker(Activity a, LinearLayout f, String savedMatter, String savedStage) {
        items.add(VAULT);
        for (DiaryStore.HCase hc : new DiaryStore(a).cases()) items.add(hc.title());
        String m = savedMatter == null ? "" : savedMatter.trim();
        if (m.isEmpty() || m.equals(VAULT_SAVED)) {
            matter = VAULT;
        } else {
            matter = m;
            if (!items.contains(m)) items.add(1, m);   // older value stays selectable
        }
        stage = savedStage == null ? "" : savedStage;

        stageBox = new LinearLayout(a);
        stageBox.setOrientation(LinearLayout.VERTICAL);
        f.addView(stageBox);
        LegalUi.label(a, stageBox, "Stage");
        final StageStore stages = new StageStore(a);
        stageTv = FieldStyle.dropdown(a, stageBox, "Select stage", v ->
                PickerDialogs.stageSheet(a, "Stage", () -> stages.list(), stage,
                        s -> {
                            stage = s;
                            refresh();
                        },
                        s -> stages.add(s),
                        (old, neu) -> {
                            stages.rename(old, neu);
                            if (old.equals(stage)) stage = neu;
                            refresh();
                        },
                        gone -> {
                            stages.delete(gone);
                            if (gone.equals(stage)) stage = "";
                            refresh();
                        },
                        (i, j) -> stages.swap(i, j)),
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LegalUi.label(a, f, "Attach to Matter (Optional)");
        matterTv = FieldStyle.dropdown(a, f, VAULT, v ->
                PickerDialogs.optionSheet(a, "Attach to Matter", "Saved cases", items,
                        items.indexOf(matter), i -> {
                            matter = items.get(i);
                            refresh();
                        }), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        refresh();
    }

    private void refresh() {
        FieldStyle.setDropdownValue(matterTv, matter, VAULT);
        FieldStyle.setDropdownValue(stageTv, stage, "Select stage");
    }

    /** Text saved on the document. */
    String matter() {
        return matter.equals(VAULT) ? VAULT_SAVED : matter;
    }

    String stage() {
        return stage;
    }

    boolean valid() {
        return true;
    }
}
