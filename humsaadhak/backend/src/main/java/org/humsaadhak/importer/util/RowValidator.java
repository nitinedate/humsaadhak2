package org.humsaadhak.importer.util;

import java.math.BigDecimal;
import java.util.*;
import org.humsaadhak.catalogue.api.NeedKind;
import org.humsaadhak.shared.model.Periodicity;

/**
 * Checks one spreadsheet row and converts its text to typed values (pure, unit-tested).
 * Accepts what people actually type: "4,50,000", "₹4,50,000", "One-time", "half yearly", "Cause".
 */
public final class RowValidator {
    public record RowError(int row, String column, String message) {}
    public record ParsedNeed(String orgRef, NeedKind kind, String name, String description, String sector, BigDecimal target, Periodicity periodicity) {}
    public record ParsedPurpose(String donorRef, String name, String description, String sector, BigDecimal amount, Periodicity periodicity) {}

    public static final int MIN_DESCRIPTION = 40;

    private RowValidator() {}

    public static Optional<ParsedNeed> need(int row, Map<String, String> v, Set<String> sectors, List<RowError> errors) {
        int before = errors.size();
        String org = required(row, v, "Organisation ref no", errors);
        NeedKind kind = kind(row, v.get("Kind"), errors);
        String name = required(row, v, "Name", errors);
        String desc = description(row, v, errors);
        String sector = sector(row, v.get("Sector"), sectors, errors);
        BigDecimal target = amount(row, "Target amount", v.get("Target amount"), true, errors);
        Periodicity per = periodicity(row, v.get("Periodicity"), errors);
        return errors.size() == before ? Optional.of(new ParsedNeed(org, kind, name, desc, sector, target, per)) : Optional.empty();
    }

    public static Optional<ParsedPurpose> purpose(int row, Map<String, String> v, Set<String> sectors, List<RowError> errors) {
        int before = errors.size();
        String donor = required(row, v, "Donor ref no", errors);
        String name = required(row, v, "Name", errors);
        String desc = description(row, v, errors);
        String sector = sector(row, v.get("Sector"), sectors, errors);
        BigDecimal amount = amount(row, "Commitment amount", v.get("Commitment amount"), false, errors);
        Periodicity per = periodicity(row, v.get("Periodicity"), errors);
        return errors.size() == before ? Optional.of(new ParsedPurpose(donor, name, desc, sector, amount, per)) : Optional.empty();
    }

    /** "₹4,50,000.50" -> 450000.50; null when blank or not a number. */
    public static BigDecimal parseAmount(String text) {
        if (text == null || text.isBlank()) return null;
        String clean = text.replace("₹", "").replace("Rs.", "").replace("Rs", "").replace(",", "").trim();
        try { return new BigDecimal(clean); } catch (NumberFormatException e) { return null; }
    }

    /** "One-time", "one time", "HALF_YEARLY", "Half-yearly", "Annual" -> enum. */
    public static Periodicity parsePeriodicity(String text) {
        if (text == null) return null;
        String k = text.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        return switch (k) {
            case "ONE_TIME", "ONCE", "ONETIME" -> Periodicity.ONE_TIME;
            case "MONTHLY" -> Periodicity.MONTHLY;
            case "QUARTERLY" -> Periodicity.QUARTERLY;
            case "HALF_YEARLY", "HALFYEARLY", "SIX_MONTHLY" -> Periodicity.HALF_YEARLY;
            case "YEARLY", "ANNUAL", "ANNUALLY" -> Periodicity.YEARLY;
            default -> null;
        };
    }

    private static String required(int row, Map<String, String> v, String col, List<RowError> errors) {
        String s = trim(v.get(col));
        if (s == null) errors.add(new RowError(row, col, "Required."));
        return s;
    }

    private static String description(int row, Map<String, String> v, List<RowError> errors) {
        String s = trim(v.get("Description"));
        if (s == null || s.length() < MIN_DESCRIPTION)
            errors.add(new RowError(row, "Description", "Write at least " + MIN_DESCRIPTION + " characters so matching works well."));
        return s;
    }

    private static NeedKind kind(int row, String text, List<RowError> errors) {
        String k = trim(text);
        if (k != null && (k.equalsIgnoreCase("cause") || k.equalsIgnoreCase("school"))) return NeedKind.CAUSE;
        if (k != null && (k.equalsIgnoreCase("project") || k.equalsIgnoreCase("ngo"))) return NeedKind.PROJECT;
        errors.add(new RowError(row, "Kind", "Use Cause or Project."));
        return null;
    }

    private static String sector(int row, String text, Set<String> sectors, List<RowError> errors) {
        String s = trim(text);
        Optional<String> match = s == null ? Optional.empty() : sectors.stream().filter(x -> x.equalsIgnoreCase(s)).findFirst();
        if (match.isEmpty()) errors.add(new RowError(row, "Sector", "Use one of: " + String.join(", ", new TreeSet<>(sectors)) + "."));
        return match.orElse(null);
    }

    private static BigDecimal amount(int row, String col, String text, boolean required, List<RowError> errors) {
        if (!required && trim(text) == null) return null;
        BigDecimal a = parseAmount(text);
        if (a == null || a.signum() <= 0) errors.add(new RowError(row, col, "Enter an amount above zero, e.g. 4,50,000."));
        return a;
    }

    private static Periodicity periodicity(int row, String text, List<RowError> errors) {
        Periodicity p = parsePeriodicity(text);
        if (p == null) errors.add(new RowError(row, "Periodicity", "Use One-time, Monthly, Quarterly, Half-yearly or Yearly."));
        return p;
    }

    private static String trim(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
