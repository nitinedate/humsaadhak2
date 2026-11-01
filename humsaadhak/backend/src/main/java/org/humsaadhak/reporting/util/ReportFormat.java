package org.humsaadhak.reporting.util;

import java.time.LocalDate;
import org.humsaadhak.shared.util.IndianRupees;

/** Financial-year dates and the number formats dashboards and reports show. */
public final class ReportFormat {
    private ReportFormat() {}

    /** 1 April of the financial year containing the day. */
    public static LocalDate fyStart(LocalDate d) { return LocalDate.of(d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1, 4, 1); }

    public static String pct(long part, long whole) { return whole == 0 ? "0%" : Math.round(100.0 * part / whole) + "%"; }

    public static String lakh(double rupees) { return "₹" + String.format("%.1f", rupees / 100000) + " L"; }

    public static String rupees(double v) { return IndianRupees.format(v); }

    /** "Direct beneficiaries (schools)" -> "direct beneficiaries (schools)". */
    public static String lower(String label) { return Character.toLowerCase(label.charAt(0)) + label.substring(1); }
}
