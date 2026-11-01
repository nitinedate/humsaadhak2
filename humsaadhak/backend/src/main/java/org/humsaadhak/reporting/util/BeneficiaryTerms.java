package org.humsaadhak.reporting.util;

import org.humsaadhak.reporting.dto.Bar;
import org.humsaadhak.reporting.dto.Drill;

import java.util.*;

/**
 * The four groups every dashboard talks about, named the way the HumSaadhak team does:
 * Individual and Corporate donors (benefactors) give to Direct beneficiaries (schools, legacy "DB")
 * and Indirect beneficiaries (NGOs, legacy "IB"). Pure, unit-tested.
 */
public final class BeneficiaryTerms {
    public static final String INDIVIDUAL = "Individual donors";
    public static final String CORPORATE = "Corporate donors";
    public static final String DIRECT = "Direct beneficiaries (schools)";
    public static final String INDIRECT = "Indirect beneficiaries (NGOs)";

    private BeneficiaryTerms() {}

    /** organisation.type -> group label. */
    public static String group(String organisationType) {
        return switch (organisationType) {
            case "DONOR_INDIVIDUAL" -> INDIVIDUAL;
            case "DONOR_CORPORATE" -> CORPORATE;
            case "SCHOOL" -> DIRECT;
            case "NGO" -> INDIRECT;
            default -> throw new IllegalArgumentException("Unknown organisation type " + organisationType);
        };
    }

    /** One organisation in a report row: "Individual donor", "Corporate donor", "Direct beneficiary (school)", "Indirect beneficiary (NGO)". */
    public static String singular(String organisationType) {
        return switch (organisationType) {
            case "DONOR_INDIVIDUAL" -> "Individual donor";
            case "DONOR_CORPORATE" -> "Corporate donor";
            case "SCHOOL" -> "Direct beneficiary (school)";
            case "NGO" -> "Indirect beneficiary (NGO)";
            default -> throw new IllegalArgumentException("Unknown organisation type " + organisationType);
        };
    }

    /** ONE_TIME -> "One-time" (null-safe). */
    public static String periodicity(String code) {
        if (code == null) return null;
        return switch (code) {
            case "ONE_TIME" -> "One-time"; case "MONTHLY" -> "Monthly"; case "QUARTERLY" -> "Quarterly";
            case "HALF_YEARLY" -> "Half-yearly"; case "YEARLY" -> "Yearly"; default -> code;
        };
    }

    /** need.kind -> who receives it: causes belong to direct beneficiaries, projects to indirect ones. */
    public static String receiver(String needKind) { return "CAUSE".equals(needKind) ? DIRECT : INDIRECT; }

    /** Dashboard title for a beneficiary organisation. */
    public static String beneficiaryTitle(String organisationType) {
        return "SCHOOL".equals(organisationType) ? "Direct beneficiary dashboard" : "Indirect beneficiary dashboard";
    }

    /** One row of "who gave to whom": donor organisation type, need kind and the amount. */
    public record Flow(String donorType, String needKind, double amount) {}

    /**
     * Money from each donor group to each beneficiary group, always all four cells (zero when nothing was given),
     * so the chart keeps the same shape: bars Individual / Corporate, series Direct / Indirect.
     */
    public static List<Bar> whoGaveToWhom(Collection<Flow> flows, String fromDate) {
        double[][] cells = new double[2][2];
        for (Flow f : flows) {
            int donor = "DONOR_CORPORATE".equals(f.donorType()) ? 1 : 0;
            int receiver = "CAUSE".equals(f.needKind()) ? 0 : 1;
            cells[donor][receiver] += f.amount();
        }
        return List.of(
                new Bar(INDIVIDUAL, List.of(cells[0][0], cells[0][1]), donationDrills("DONOR_INDIVIDUAL", fromDate)),
                new Bar(CORPORATE, List.of(cells[1][0], cells[1][1]), donationDrills("DONOR_CORPORATE", fromDate)));
    }

    /** Clicking a "who gave to whom" bar lists that donor group's donations to direct (causes) or indirect (projects) beneficiaries. */
    private static List<Drill> donationDrills(String donorType, String fromDate) {
        return List.of(Drill.of("DONATIONS", "donorType", donorType, "needKind", "CAUSE", "from", fromDate),
                Drill.of("DONATIONS", "donorType", donorType, "needKind", "PROJECT", "from", fromDate));
    }

    /** Indian financial-year quarter of a calendar month (1-12): April-June is Q1, January-March is Q4. */
    public static String fyQuarter(int month) { return "Q" + (((month + 8) % 12) / 3 + 1); }

    /** "4 direct and 2 indirect beneficiaries" / "1 direct beneficiary" / "none yet". */
    public static String supportedNote(long direct, long indirect) {
        if (direct == 0 && indirect == 0) return "none yet";
        List<String> parts = new ArrayList<>();
        if (direct > 0) parts.add(direct + " direct");
        if (indirect > 0) parts.add(indirect + " indirect");
        return String.join(" and ", parts) + " beneficiar" + (direct + indirect == 1 ? "y" : "ies");
    }

    /** "2 corporate, 3 individual" (zero groups left out). */
    public static String donorMixNote(long corporate, long individual) {
        List<String> parts = new ArrayList<>();
        if (corporate > 0) parts.add(corporate + " corporate");
        if (individual > 0) parts.add(individual + " individual");
        return parts.isEmpty() ? "no donations yet" : String.join(", ", parts);
    }
}
