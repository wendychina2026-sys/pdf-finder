package com.example.twobuttons;

import android.content.Context;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads saved file details + cases and answers: upcoming, today, search. */
class CaseItems {

    static class Item {
        String path = "";
        String fileName = "";
        String caseTitle = "";
        String caseType = "";
        String subType = "";
        String subSubType = "";
        String court = "";
        String nextDate = "";   // yyyy-MM-dd or ""

        /** Case name only. File name is never shown on the dashboard. */
        String title() {
            return caseTitle.isEmpty() ? "(No case name)" : caseTitle;
        }

        /** case type | sub case type */
        String subtitle() {
            if (caseType.isEmpty()) return subType;
            if (subType.isEmpty()) return caseType;
            return caseType + "  |  " + subType;
        }

        String initial() {
            String t = caseTitle.trim();
            return t.isEmpty() ? "?" : t.substring(0, 1).toUpperCase(Locale.ROOT);
        }
    }

    static String todayIso() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** 2025-08-30 -> 30 Aug, 2025 */
    static String pretty(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso);
            return new SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(d);
        } catch (ParseException e) {
            return iso;
        }
    }

    /** Every PDF that has saved file details and still exists. */
    static List<Item> all(Context c) {
        List<Item> out = new ArrayList<>();
        Map<String, DetailsStore.Details> map = new DetailsStore(c).everything();
        for (Map.Entry<String, DetailsStore.Details> e : map.entrySet()) {
            File f = new File(e.getKey());
            if (!f.exists()) continue;
            DetailsStore.Details d = e.getValue();
            Item it = new Item();
            it.path = e.getKey();
            it.fileName = f.getName();
            it.caseType = d.caseType;
            it.subType = d.subType;
            it.subSubType = d.subSubType;
            it.court = d.court;
            it.nextDate = d.nextDate;
            it.caseTitle = d.caseName;
            out.add(it);
        }
        return out;
    }

    /** nextDate after today, soonest first. */
    static List<Item> upcoming(Context c) {
        String today = todayIso();
        List<Item> out = new ArrayList<>();
        for (Item it : all(c)) {
            if (!it.nextDate.isEmpty() && it.nextDate.compareTo(today) > 0) out.add(it);
        }
        Collections.sort(out, (a, b) -> {
            int r = a.nextDate.compareTo(b.nextDate);
            return r != 0 ? r : a.title().compareToIgnoreCase(b.title());
        });
        return out;
    }

    /** nextDate is today. */
    static List<Item> today(Context c) {
        String today = todayIso();
        List<Item> out = new ArrayList<>();
        for (Item it : all(c)) {
            if (today.equals(it.nextDate)) out.add(it);
        }
        Collections.sort(out, (a, b) -> a.title().compareToIgnoreCase(b.title()));
        return out;
    }

    /** Today's cases whose case name contains query. Empty query = all. */
    static List<Item> todayMatching(Context c, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Item> out = new ArrayList<>();
        for (Item it : today(c)) {
            if (q.isEmpty() || it.caseTitle.toLowerCase(Locale.ROOT).contains(q)) out.add(it);
        }
        return out;
    }
}
