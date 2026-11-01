package org.humsaadhak.reporting.dao;

import org.humsaadhak.reporting.dto.ReportColumn;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.humsaadhak.shared.exception.DomainException;

/**
 * The beneficiary reports of the original project (Donation, Match, Project/Cause and Customize Project/Cause reports),
 * rebuilt safely. Pure and unit-tested.
 *
 * The original custom report assembled SQL from the column_mapping/join_mapping tables (security finding S7). Here every
 * column, join and filter is a constant in this class; a request can only switch columns on (by enum name) and give
 * filter values, which are bound as parameters. The original's project/cause totals joined matches and donations in one
 * query and so double-counted; here each total is its own subquery.
 */
public final class BeneficiaryReportQuery {
    public enum Type { DONATIONS, MATCHES, NEEDS, CUSTOM }

    /** Columns of the Customize report, in the original order. Labels follow the organisation: Project (NGO) or Cause (school). */
    public enum CustomColumn {
        NEED("Project", "Cause"), PENDING_NEED("Project (yet to be approved)", "Cause (yet to be approved)"),
        APPROVED_DATE("Approved Date"), MATCHED_FLAG("Matched Flag"), PURPOSE_ID("Purpose Id"), PURPOSE_NAME("Purpose Name"),
        COMMITMENT_AMOUNT("Commitment Amount"), COMMITMENT_DATE("Commitment Date"), DONATION_AMOUNT("Donation Amount"),
        DONATION_DATE("Donation Date"), DONOR_NAME("Donor Name"), DONOR_TYPE("Donor Type"),
        UPCOMING_AMOUNT("Upcoming Donation Amount"), UPCOMING_DATE("Upcoming Donation Date"),
        DOC_STATUS("Project Doc status", "Cause Doc status"), PERIODICITY("Periodicity");

        private final String ngoLabel, schoolLabel;
        CustomColumn(String label) { this(label, label); }
        CustomColumn(String ngoLabel, String schoolLabel) { this.ngoLabel = ngoLabel; this.schoolLabel = schoolLabel; }
        public String label(boolean school) { return school ? schoolLabel : ngoLabel; }
    }

    public record Filter(long orgId, boolean school, Long needId, String financialYear, LocalDate approvedFrom, LocalDate approvedTo,
                  List<CustomColumn> columns) {}
    public record Query(String select, String countSql, List<Object> params, List<ReportColumn> columns) {}

    public static final int MAX_EXPORT_ROWS = 10_000;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private BeneficiaryReportQuery() {}

    public static Type type(String name) {
        try { return Type.valueOf(name.toUpperCase(Locale.ROOT)); } catch (RuntimeException e) { throw bad("Unknown report " + name); }
    }

    public static List<CustomColumn> customColumns(List<String> names) {
        if (names == null || names.isEmpty()) throw bad("Choose at least one column.");
        List<CustomColumn> out = new ArrayList<>();
        for (String n : names) {
            try { CustomColumn c = CustomColumn.valueOf(n); if (!out.contains(c)) out.add(c); }
            catch (RuntimeException e) { throw bad("Unknown column " + n); }
        }
        out.sort(Comparator.naturalOrder());     // always the original column order, whatever order they were ticked in
        return out;
    }

    /** "2025-2026" -> 1 April 2025 to 31 March 2026 (the original used 31 March to 31 March, which double-counted that day). */
    public static LocalDate[] financialYear(String fy) {
        String[] parts = fy == null ? new String[0] : fy.split("-");
        try {
            int start = Integer.parseInt(parts[0]), end = Integer.parseInt(parts[1]);
            if (parts.length != 2 || end != start + 1) throw new NumberFormatException();
            return new LocalDate[] { LocalDate.of(start, 4, 1), LocalDate.of(end, 3, 31) };
        } catch (RuntimeException e) {
            throw bad("A financial year looks like 2025-2026.");
        }
    }

    /** Financial years to offer, newest first, from the first one with data to the current one. */
    public static List<String> years(LocalDate firstActivity, LocalDate today) {
        int first = fyStartYear(firstActivity == null ? today : firstActivity);
        int last = fyStartYear(today);
        List<String> out = new ArrayList<>();
        for (int y = last; y >= first; y--) out.add(y + "-" + (y + 1));
        return out;
    }

    /** The calendar year in which the financial year containing this day began (April onwards). */
    public static int fyStartYear(LocalDate d) { return d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1; }

    public static Query build(Type type, Filter f) {
        List<Object> params = new ArrayList<>();
        List<String> where = new ArrayList<>(List.of("n.org_id = ?"));
        params.add(f.orgId());
        if (f.needId() != null) { where.add("n.id = ?"); params.add(f.needId()); }

        boolean pending = type == Type.CUSTOM && f.columns().contains(CustomColumn.PENDING_NEED);
        LocalDate[] range = range(f);
        if (pending) {
            if (range != null) throw bad("Approval dates do not apply to causes or projects that are not approved yet.");
            where.add("not n.approved");
        } else {
            where.add("n.approved");
            if (range != null) {
                where.add("n.approved_at >= ? and n.approved_at < ?");
                params.add(range[0].atStartOfDay(java.time.ZoneOffset.UTC).toOffsetDateTime());
                params.add(range[1].plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toOffsetDateTime());
            }
        }
        String need = f.school() ? "Cause" : "Project";
        String whereSql = " where " + String.join(" and ", where);

        return switch (type) {
            case DONATIONS -> paged("""
                    select n.name as need, n.ref_no as "needRef", donor.name as "donorName", donor.type as "_donorType",
                           p.commitment_amount as "commitmentAmount", p.commitment_date as "commitmentDate",
                           coalesce(p.periodicity, n.periodicity) as "_periodicity", d.paid_amount as "donationAmount", d.paid_on as "donationDate\"""",
                    " from donation d join need n on n.id = d.need_id join organisation donor on donor.id = d.donor_org_id"
                            + " left join purpose p on p.id = d.purpose_id", whereSql, " order by d.paid_on desc, d.id desc", params,
                    List.of(text("need", need + " Name"), text("needRef", need + " Id"), text("donorName", "Donor Name"), text("donorType", "Donor Type"),
                            money("commitmentAmount", "Commitment Amount"), date("commitmentDate", "Commitment Date"), text("periodicity", "Periodicity"),
                            money("donationAmount", "Donation Amount"), date("donationDate", "Donation Date")));
            case MATCHES -> paged("""
                    select n.name as need, n.ref_no as "needRef", p.ref_no as "purposeRef", p.name as "purposeName",
                           p.commitment_amount as "commitmentAmount", p.commitment_date as "commitmentDate", p.periodicity as "_periodicity\"""",
                    " from match m join need n on n.id = m.need_id join purpose p on p.id = m.purpose_id", whereSql,
                    " order by n.name, m.priority", params,
                    List.of(text("need", need + " Name"), text("needRef", need + " Id"), text("purposeRef", "Purpose ID"), text("purposeName", "Purpose Name"),
                            money("commitmentAmount", "Commitment Amount"), date("commitmentDate", "Commitment Date"), text("periodicity", "Periodicity")));
            case NEEDS -> paged("""
                    select n.name as need, n.ref_no as "needRef", n.approved_at as "approvedDate",
                           case when exists (select 1 from match m where m.need_id = n.id) then 'Y' else 'N' end as matched,
                           case when exists (select 1 from donation d where d.need_id = n.id) then 'Y' else 'N' end as donated,
                           (select coalesce(sum(p.commitment_amount), 0) from match m join purpose p on p.id = m.purpose_id where m.need_id = n.id) as "commitmentTotal",
                           (select coalesce(sum(d.paid_amount), 0) from donation d where d.need_id = n.id) as "donationTotal\"""",
                    " from need n", whereSql, " order by n.name", params,
                    List.of(text("need", need + " Name"), text("needRef", need + " Id"), date("approvedDate", "Approved Date"), text("matched", "Matched (Y/N)"),
                            text("donated", "Donation Received (Y/N)"), money("commitmentTotal", "Sum of Commitment Amount (matched)"),
                            money("donationTotal", "Sum of Donation Amount")));
            case CUSTOM -> custom(f, params, whereSql, need);
        };
    }

    private static Query custom(Filter f, List<Object> params, String whereSql, String need) {
        List<CustomColumn> cols = f.columns();
        boolean hasNeed = cols.contains(CustomColumn.NEED), hasPending = cols.contains(CustomColumn.PENDING_NEED);
        if (hasNeed == hasPending)
            throw bad("Choose either “" + CustomColumn.NEED.label(f.school()) + "” or “" + CustomColumn.PENDING_NEED.label(f.school()) + "”.");
        EnumSet<CustomColumn> matchCols = EnumSet.of(CustomColumn.MATCHED_FLAG, CustomColumn.PURPOSE_ID, CustomColumn.PURPOSE_NAME,
                CustomColumn.COMMITMENT_AMOUNT, CustomColumn.COMMITMENT_DATE, CustomColumn.DONATION_AMOUNT, CustomColumn.DONATION_DATE,
                CustomColumn.DONOR_NAME, CustomColumn.DONOR_TYPE, CustomColumn.UPCOMING_AMOUNT, CustomColumn.UPCOMING_DATE);
        EnumSet<CustomColumn> donationCols = EnumSet.of(CustomColumn.DONATION_AMOUNT, CustomColumn.DONATION_DATE, CustomColumn.DONOR_NAME, CustomColumn.DONOR_TYPE);
        boolean joinMatch = cols.stream().anyMatch(matchCols::contains);
        boolean joinDonation = cols.stream().anyMatch(donationCols::contains);
        boolean joinDonor = cols.contains(CustomColumn.DONOR_NAME) || cols.contains(CustomColumn.DONOR_TYPE);
        boolean joinUpcoming = cols.contains(CustomColumn.UPCOMING_AMOUNT) || cols.contains(CustomColumn.UPCOMING_DATE);

        List<String> select = new ArrayList<>(List.of("n.name as need", "n.ref_no as \"needRef\""));
        List<ReportColumn> columns = new ArrayList<>(List.of(text("need", CustomColumn.NEED.label(f.school())), text("needRef", need + " Id")));
        for (CustomColumn c : cols) {
            String label = c.label(f.school());
            switch (c) {
                case NEED, PENDING_NEED -> { }
                case APPROVED_DATE -> { select.add("n.approved_at as \"approvedDate\""); columns.add(date("approvedDate", label)); }
                case MATCHED_FLAG -> { select.add("case when m.id is null then 'No' else 'Yes' end as \"matchedFlag\""); columns.add(text("matchedFlag", label)); }
                case PURPOSE_ID -> { select.add("p.ref_no as \"purposeRef\""); columns.add(text("purposeRef", label)); }
                case PURPOSE_NAME -> { select.add("p.name as \"purposeName\""); columns.add(text("purposeName", label)); }
                case COMMITMENT_AMOUNT -> { select.add("p.commitment_amount as \"commitmentAmount\""); columns.add(money("commitmentAmount", label)); }
                case COMMITMENT_DATE -> { select.add("p.commitment_date as \"commitmentDate\""); columns.add(date("commitmentDate", label)); }
                case DONATION_AMOUNT -> { select.add("d.paid_amount as \"donationAmount\""); columns.add(money("donationAmount", label)); }
                case DONATION_DATE -> { select.add("d.paid_on as \"donationDate\""); columns.add(date("donationDate", label)); }
                case DONOR_NAME -> { select.add("donor.name as \"donorName\", donor.ref_no as \"_donorRef\""); columns.add(text("donorName", label)); }
                case DONOR_TYPE -> { select.add("donor.type as \"_donorType\""); columns.add(text("donorType", label)); }
                case UPCOMING_AMOUNT -> { select.add("up.amount as \"upcomingAmount\""); columns.add(money("upcomingAmount", label)); }
                case UPCOMING_DATE -> { select.add("up.due_date as \"upcomingDate\""); columns.add(date("upcomingDate", label)); }
                case DOC_STATUS -> {
                    select.add("case when exists (select 1 from document doc where doc.need_id = n.id and doc.state = 'REVIEWED' and not doc.superseded)"
                            + " then 'Approved' else 'Not approved' end as \"docStatus\"");
                    columns.add(text("docStatus", label));
                }
                case PERIODICITY -> { select.add("n.periodicity as \"_periodicity\""); columns.add(text("periodicity", label)); }
            }
        }
        StringBuilder from = new StringBuilder(" from need n");
        if (joinMatch) from.append(" left join match m on m.need_id = n.id left join purpose p on p.id = m.purpose_id");
        if (joinDonation) from.append(" left join donation d on d.need_id = n.id and d.purpose_id = p.id");
        if (joinDonor) from.append(" left join organisation donor on donor.id = coalesce(d.donor_org_id, m.donor_org_id)");
        if (joinUpcoming) from.append("""
                 left join lateral (select i.due_date, i.amount from recurring_plan rp join instalment i on i.plan_id = rp.id
                   where rp.need_id = n.id and rp.donor_org_id = m.donor_org_id and rp.active and i.status = 'DUE' and i.due_date >= current_date
                   order by i.due_date limit 1) up on true""".replace('\n', ' '));
        String order = " order by n.name" + (joinMatch ? ", m.priority" : "") + (joinDonation ? ", d.paid_on" : "");
        return paged("select " + String.join(", ", select), from.toString(), whereSql, order, params, columns);
    }

    /** The two header lines the original printed above every exported report. */
    public static String entityLine(String orgName, String refNo) { return "Entity Name - " + orgName + " [ID: " + refNo + "]"; }

    public static String filterLine(Filter f, String needName) {
        List<String> parts = new ArrayList<>();
        LocalDate[] r = range(f);
        if (f.financialYear() != null && !f.financialYear().isBlank()) parts.add("Financial year - " + f.financialYear());
        else if (r != null) parts.add("Approved between - " + DAY.format(r[0]) + " to " + DAY.format(r[1]));
        if (needName != null) parts.add((f.school() ? "Cause - " : "Project - ") + needName);
        if (f.columns() != null && f.columns().contains(CustomColumn.PENDING_NEED)) parts.add("Not yet approved");
        return parts.isEmpty() ? "All " + (f.school() ? "causes" : "projects") : String.join("; ", parts);
    }

    private static LocalDate[] range(Filter f) {
        boolean year = f.financialYear() != null && !f.financialYear().isBlank();
        boolean dates = f.approvedFrom() != null || f.approvedTo() != null;
        if (year && dates) throw bad("Choose a financial year or approval dates, not both.");
        if (year) return financialYear(f.financialYear());
        if (!dates) return null;
        if (f.approvedFrom() == null || f.approvedTo() == null) throw bad("Give both the approved start and end dates.");
        if (f.approvedTo().isBefore(f.approvedFrom())) throw bad("The end date is before the start date.");
        return new LocalDate[] { f.approvedFrom(), f.approvedTo() };
    }

    private static Query paged(String select, String from, String where, String order, List<Object> params, List<ReportColumn> columns) {
        return new Query(select + from + where + order + " limit ? offset ?", "select count(*)" + from + where, params, columns);
    }

    private static ReportColumn text(String key, String label) { return new ReportColumn(key, label, "text"); }
    private static ReportColumn money(String key, String label) { return new ReportColumn(key, label, "money"); }
    private static ReportColumn date(String key, String label) { return new ReportColumn(key, label, "date"); }
    private static DomainException bad(String message) { return new DomainException("bad-report", message); }
}
