package com.example.twobuttons;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Country dialling codes, per-country mobile length rules, number parsing and a picker dialog. */
final class CountryCodes {

    static final String DEFAULT_DIAL = "+91";

    // iso, name, dial code, min digits, max digits (mobile number without country code / trunk 0)
    private static final Object[][] DATA = {
            {"IN", "India", "+91", 10, 10},
            {"US", "United States / Canada", "+1", 10, 10},
            {"GB", "United Kingdom", "+44", 10, 10},
            {"CN", "China", "+86", 11, 11},
            {"AE", "United Arab Emirates", "+971", 9, 9},
            {"SA", "Saudi Arabia", "+966", 9, 9},
            {"BD", "Bangladesh", "+880", 10, 10},
            {"PK", "Pakistan", "+92", 10, 10},
            {"LK", "Sri Lanka", "+94", 9, 9},
            {"NP", "Nepal", "+977", 10, 10},
            {"BT", "Bhutan", "+975", 8, 8},
            {"MV", "Maldives", "+960", 7, 7},
            {"AF", "Afghanistan", "+93", 9, 9},
            {"SG", "Singapore", "+65", 8, 8},
            {"HK", "Hong Kong", "+852", 8, 8},
            {"TW", "Taiwan", "+886", 9, 9},
            {"JP", "Japan", "+81", 10, 10},
            {"KR", "South Korea", "+82", 9, 10},
            {"MY", "Malaysia", "+60", 9, 10},
            {"ID", "Indonesia", "+62", 9, 12},
            {"TH", "Thailand", "+66", 9, 9},
            {"VN", "Vietnam", "+84", 9, 9},
            {"PH", "Philippines", "+63", 10, 10},
            {"MM", "Myanmar", "+95", 8, 10},
            {"KH", "Cambodia", "+855", 8, 9},
            {"LA", "Laos", "+856", 8, 10},
            {"MN", "Mongolia", "+976", 8, 8},
            {"AU", "Australia", "+61", 9, 9},
            {"NZ", "New Zealand", "+64", 8, 10},
            {"QA", "Qatar", "+974", 8, 8},
            {"KW", "Kuwait", "+965", 8, 8},
            {"OM", "Oman", "+968", 8, 8},
            {"BH", "Bahrain", "+973", 8, 8},
            {"IL", "Israel", "+972", 9, 9},
            {"JO", "Jordan", "+962", 9, 9},
            {"LB", "Lebanon", "+961", 7, 8},
            {"IQ", "Iraq", "+964", 10, 10},
            {"IR", "Iran", "+98", 10, 10},
            {"TR", "Turkey", "+90", 10, 10},
            {"EG", "Egypt", "+20", 10, 10},
            {"ZA", "South Africa", "+27", 9, 9},
            {"NG", "Nigeria", "+234", 10, 10},
            {"KE", "Kenya", "+254", 9, 9},
            {"GH", "Ghana", "+233", 9, 9},
            {"ET", "Ethiopia", "+251", 9, 9},
            {"TZ", "Tanzania", "+255", 9, 9},
            {"UG", "Uganda", "+256", 9, 9},
            {"MA", "Morocco", "+212", 9, 9},
            {"DZ", "Algeria", "+213", 9, 9},
            {"TN", "Tunisia", "+216", 8, 8},
            {"MU", "Mauritius", "+230", 8, 8},
            {"RU", "Russia / Kazakhstan", "+7", 10, 10},
            {"DE", "Germany", "+49", 10, 11},
            {"FR", "France", "+33", 9, 9},
            {"IT", "Italy", "+39", 9, 10},
            {"ES", "Spain", "+34", 9, 9},
            {"PT", "Portugal", "+351", 9, 9},
            {"NL", "Netherlands", "+31", 9, 9},
            {"BE", "Belgium", "+32", 8, 9},
            {"CH", "Switzerland", "+41", 9, 9},
            {"AT", "Austria", "+43", 10, 13},
            {"SE", "Sweden", "+46", 9, 9},
            {"NO", "Norway", "+47", 8, 8},
            {"DK", "Denmark", "+45", 8, 8},
            {"FI", "Finland", "+358", 9, 10},
            {"IE", "Ireland", "+353", 9, 9},
            {"PL", "Poland", "+48", 9, 9},
            {"CZ", "Czech Republic", "+420", 9, 9},
            {"GR", "Greece", "+30", 10, 10},
            {"RO", "Romania", "+40", 9, 9},
            {"HU", "Hungary", "+36", 9, 9},
            {"UA", "Ukraine", "+380", 9, 9},
            {"MX", "Mexico", "+52", 10, 10},
            {"BR", "Brazil", "+55", 10, 11},
            {"AR", "Argentina", "+54", 10, 10},
            {"CL", "Chile", "+56", 9, 9},
            {"CO", "Colombia", "+57", 10, 10},
            {"PE", "Peru", "+51", 9, 9},
            {"FJ", "Fiji", "+679", 7, 7},
    };

    static final class Country {
        final String iso, name, dial;
        final int min, max;
        Country(String iso, String name, String dial, int min, int max) {
            this.iso = iso; this.name = name; this.dial = dial; this.min = min; this.max = max;
        }
        String flag() {
            int a = Character.codePointAt(iso, 0) - 'A' + 0x1F1E6;
            int b = Character.codePointAt(iso, 1) - 'A' + 0x1F1E6;
            return new String(Character.toChars(a)) + new String(Character.toChars(b));
        }
    }

    private static final List<Country> ALL = new ArrayList<>();
    static {
        for (Object[] r : DATA) {
            ALL.add(new Country((String) r[0], (String) r[1], (String) r[2], (Integer) r[3], (Integer) r[4]));
        }
    }

    private CountryCodes() {}

    static Country byDial(String dial) {
        for (Country c : ALL) if (c.dial.equals(dial)) return c;
        return ALL.get(0);
    }

    /** "🇮🇳 +91" */
    static String label(String dial) {
        return byDial(dial).flag() + " " + dial;
    }

    static String digits(String s) {
        return s == null ? "" : s.replaceAll("[^0-9]", "");
    }

    /**
     * Splits a raw phone string into {dial, nationalDigits}. A leading + or 00 means the number
     * carries its own country code; otherwise the default code is used.
     */
    static String[] parse(String raw, String defaultDial) {
        String s = raw == null ? "" : raw.trim();
        boolean plus = s.startsWith("+");
        String d = digits(s);
        if (!plus && d.startsWith("00")) {
            plus = true;
            d = d.substring(2);
        }
        if (plus) {
            for (int len = 4; len >= 1; len--) {
                if (d.length() <= len) continue;
                String cand = "+" + d.substring(0, len);
                for (Country c : ALL) {
                    if (c.dial.equals(cand)) return new String[]{cand, d.substring(len)};
                }
            }
            return new String[]{defaultDial, d};
        }
        if (DEFAULT_DIAL.equals(defaultDial)) {
            if (d.length() == 11 && d.startsWith("0")) d = d.substring(1);
            else if (d.length() == 12 && d.startsWith("91")) d = d.substring(2);
        }
        return new String[]{defaultDial, d};
    }

    /** Returns null if the mobile number is valid for the country code, else an error message. */
    static String mobileError(String dial, String nationalDigits) {
        Country c = byDial(dial);
        int n = nationalDigits.length();
        if (n >= c.min && n <= c.max) return null;
        if (c.min == c.max) return "Phone number must be " + c.min + " digits";
        return "Phone number must be " + c.min + " to " + c.max + " digits";
    }

    /** Fixed-line numbers include an area code, lengths vary a lot, so the rule is loose. */
    static String landlineError(String nationalDigits) {
        int n = nationalDigits.length();
        if (n >= 6 && n <= 12) return null;
        return "Landline must be 6 to 12 digits (with area code)";
    }

    interface OnPick { void picked(String dial); }

    /** Searchable country code list. */
    static void pick(Activity a, String current, OnPick cb) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = CaseViews.dp(a, 16);
        box.setPadding(pad, pad / 2, pad, 0);

        EditText search = new EditText(a);
        search.setHint("Search country or code");
        search.setSingleLine(true);
        box.addView(search);

        List<Country> shown = new ArrayList<>(ALL);
        List<String> rows = new ArrayList<>();
        ArrayAdapter<String> ad = new ArrayAdapter<>(a, android.R.layout.simple_list_item_1, rows);
        ListView lv = new ListView(a);
        lv.setAdapter(ad);
        box.addView(lv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, CaseViews.dp(a, 360)));

        Runnable fill = () -> {
            String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            shown.clear();
            rows.clear();
            for (Country c : ALL) {
                if (q.isEmpty() || c.name.toLowerCase(Locale.ROOT).contains(q) || c.dial.contains(q)) {
                    shown.add(c);
                    rows.add(c.flag() + "  " + c.name + "  " + c.dial);
                }
            }
            ad.notifyDataSetChanged();
        };
        fill.run();
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { fill.run(); }
        });

        AlertDialog dlg = new AlertDialog.Builder(a)
                .setTitle("Select country code")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .create();
        lv.setOnItemClickListener((p, v, pos, id) -> {
            cb.picked(shown.get(pos).dial);
            dlg.dismiss();
        });
        dlg.show();
    }
}
