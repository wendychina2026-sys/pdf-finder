package com.example.twobuttons;

import android.content.Context;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Reads saved file details + cases and answers: upcoming, today, search. */
class CaseItems {

    static class Item {
        String path = "";
        String fileName = "";
        String caseTitle = "";
        String caseNumber = "";
        String caseType = "";
        String subType = "";
        String subSubType = "";
        String court = "";
        String nextDate = "";   // yyyy-MM-dd or ""

        String title() {
            if (!caseTitle.isEmpty()) return caseTitle;
            if (fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                return fileName.substring(0, fileName.length() - 4);
            }
            return fileName;
        }

        String subtitle() {
            String s = !subType.isEmpty() ? subType : caseType;
            if (!caseNumber.isEmpty()) {
                s = s.isEmpty() ? "No. " + caseNumber : s + "  |  No. " + caseNumber;
            }
            return s;
        }

        String initial() {
            String t = title().trim();
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
        Map<String, CaseEntry> byId = new HashMap<>();
        for (CaseEntry e : new CaseStore(c).load()) byId.put(e.id, e);

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
            CaseEntry ce = d.caseId.isEmpty() ? null : byId.get(d.caseId);
            if (ce != null) {
                it.caseTitle = ce.title;
                it.caseNumber = ce.number;
            }
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

    /** Match case name, case number or file name. Also lists cases with no file linked. */
    static List<Item> search(Context c, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Item> out = new ArrayList<>();
        if (q.isEmpty()) return out;

        Set<String> linked = new HashSet<>();
        Map<String, DetailsStore.Details> map = new DetailsStore(c).everything();
        for (DetailsStore.Details d : map.values()) {
            if (!d.caseId.isEmpty()) linked.add(d.caseId);
        }

        for (Item it : all(c)) {
            String hay = (it.title() + " " + it.caseNumber + " " + it.fileName)
                    .toLowerCase(Locale.ROOT);
            if (hay.contains(q)) out.add(it);
        }

        for (CaseEntry e : new CaseStore(c).load()) {
            if (linked.contains(e.id)) continue;
            String hay = (e.title + " " + e.number).toLowerCase(Locale.ROOT);
            if (!hay.contains(q)) continue;
            Item it = new Item();
            it.caseTitle = e.title;
            it.caseNumber = e.number;
            it.caseType = e.type;
            out.add(it);
        }

        Collections.sort(out, (a, b) -> a.title().compareToIgnoreCase(b.title()));
        return out;
    }
}
