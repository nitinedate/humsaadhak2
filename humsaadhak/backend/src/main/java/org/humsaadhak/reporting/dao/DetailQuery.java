package org.humsaadhak.reporting.dao;

import org.humsaadhak.reporting.dto.DetailColumn;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.humsaadhak.shared.exception.DomainException;

/**
 * Builds the SQL behind a chart segment ("drill-down"). Pure and unit-tested.
 * Safety: every filter key and value is checked against an allow-list (or parsed as a number/date) and bound as a
 * parameter; the person's scope is always added, so donors see only their own giving and beneficiaries only their
 * own organisation, whatever the request says.
 */
public final class DetailQuery {
    public enum Set { ORGANISATIONS, NEEDS, DONATIONS, PURPOSES }

    /** Who is asking: staff see everything; donors and beneficiaries are limited to their organisation. */
    public record Scope(boolean staff, Long donorOrgId, Long beneficiaryOrgId) {
        public static Scope staffScope() { return new Scope(true, null, null); }
        public static Scope donor(long orgId) { return new Scope(false, orgId, null); }
        public static Scope beneficiary(long orgId) { return new Scope(false, null, orgId); }
    }

    /** The person may not open this list (mapped to HTTP 403 by DetailController). */
    public static final class NotAllowed extends RuntimeException { public NotAllowed(String m) { super(m); } }
    public record Sql(String select, String countSql, List<Object> params, List<DetailColumn> columns) {}

    public static final int MAX_ROWS = 500;
    private static final java.util.Set<String> ORG_TYPES = java.util.Set.of("DONOR_INDIVIDUAL", "DONOR_CORPORATE", "SCHOOL", "NGO");
    private static final java.util.Set<String> KINDS = java.util.Set.of("CAUSE", "PROJECT");

    public static final List<DetailColumn> ORGANISATION_COLUMNS = List.of(col("ref", "Reference"), col("name", "Name"), col("group", "Group"),
            col("city", "City"), col("state", "State"), new DetailColumn("verified", "Verified", "yesno"), new DetailColumn("registered", "Registered on", "date"),
            new DetailColumn("needs", "Causes or projects", "count"), new DetailColumn("given", "Given", "money"), new DetailColumn("raised", "Received", "money"));
    public static final List<DetailColumn> NEED_COLUMNS = List.of(col("ref", "Reference"), col("name", "Name"), col("kind", "Kind"),
            col("beneficiary", "Beneficiary"), col("sector", "Sector"), new DetailColumn("target", "Target", "money"),
            new DetailColumn("raised", "Raised", "money"), new DetailColumn("remaining", "Still needed", "money"));
    public static final List<DetailColumn> DONATION_COLUMNS = List.of(col("ref", "Reference"), new DetailColumn("paidOn", "Paid on", "date"),
            col("donor", "Donor"), col("donorGroup", "Donor type"), col("need", "Cause or project"), col("beneficiary", "Beneficiary"),
            col("beneficiaryGroup", "Beneficiary type"), new DetailColumn("amount", "Paid", "money"));
    public static final List<DetailColumn> PURPOSE_COLUMNS = List.of(col("ref", "Reference"), col("name", "Purpose"), col("donor", "Donor"),
            col("sector", "Sector"), new DetailColumn("commitment", "Commitment", "money"), col("periodicity", "How often"),
            new DetailColumn("matches", "Confirmed matches", "count"), new DetailColumn("given", "Given", "money"));

    private DetailQuery() {}

    public static Set set(String name) {
        try { return Set.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (RuntimeException e) { throw bad("Unknown detail set " + name); }
    }

    public static Sql build(Set set, Map<String, String> filters, Scope scope) {
        List<String> where = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        Map<String, String> f = new HashMap<>(filters);
        String select;
        String fromSql;
        List<DetailColumn> columns;
        String orderBy;

        switch (set) {
            case ORGANISATIONS -> {
                if (!scope.staff()) throw new NotAllowed("organisation lists are for staff");
                take(f, "type").ifPresent(t -> { where.add("o.type = ?"); params.add(allowed(t, ORG_TYPES, "type")); });
                take(f, "group").ifPresent(g -> where.add(switch (g) {
                    case "DONORS" -> "o.type like 'DONOR_%'";
                    case "BENEFICIARIES" -> "o.type in ('SCHOOL','NGO')";
                    default -> throw bad("Unknown group " + g);
                }));
                take(f, "verified").ifPresent(v -> { where.add("o.verified = ?"); params.add(bool(v)); });
                select = """
                    select o.id as "_id", o.ref_no as ref, o.name, o.type as "_type", o.city, o.state, o.verified, o.created_at::date as registered,
                           (select count(*) from need n where n.org_id = o.id and n.approved) as needs,
                           (select coalesce(sum(d.paid_amount),0) from donation d where d.donor_org_id = o.id) as given,
                           (select coalesce(sum(n.raised_amount),0) from need n where n.org_id = o.id) as raised
                    """;
                fromSql = " from organisation o";
                columns = ORGANISATION_COLUMNS;
                orderBy = " order by o.name";
            }
            case NEEDS -> {
                if (scope.donorOrgId() != null) throw new NotAllowed("needs lists are for staff and beneficiaries");
                if (scope.beneficiaryOrgId() != null) { where.add("n.org_id = ?"); params.add(scope.beneficiaryOrgId()); }
                take(f, "kind").ifPresent(k -> { where.add("n.kind = ?"); params.add(allowed(k, KINDS, "kind")); });
                take(f, "approved").ifPresent(v -> { where.add("n.approved = ?"); params.add(bool(v)); });
                take(f, "id").ifPresent(v -> { where.add("n.id = ?"); params.add(number(v)); });
                take(f, "state").ifPresent(s -> where.add(needState(s)));
                select = """
                    select n.id as "_id", n.ref_no as ref, n.name, case when n.kind = 'CAUSE' then 'Cause' else 'Project' end as kind,
                           o.name as beneficiary, n.sector, n.target_amount as target, n.raised_amount as raised,
                           greatest(n.target_amount - n.raised_amount, 0) as remaining
                    """;
                fromSql = " from need n join organisation o on o.id = n.org_id";
                columns = NEED_COLUMNS;
                orderBy = " order by o.name, n.name";
            }
            case DONATIONS -> {
                if (scope.donorOrgId() != null) { where.add("d.donor_org_id = ?"); params.add(scope.donorOrgId()); }
                if (scope.beneficiaryOrgId() != null) { where.add("n.org_id = ?"); params.add(scope.beneficiaryOrgId()); }
                take(f, "donorType").ifPresent(t -> { where.add("donor.type = ?"); params.add(allowed(t, java.util.Set.of("DONOR_INDIVIDUAL", "DONOR_CORPORATE"), "donorType")); });
                take(f, "needKind").ifPresent(k -> { where.add("n.kind = ?"); params.add(allowed(k, KINDS, "needKind")); });
                take(f, "needId").ifPresent(v -> { where.add("n.id = ?"); params.add(number(v)); });
                take(f, "sector").ifPresent(s -> { where.add("n.sector = ?"); params.add(s); });
                LocalDate[] range = period(f);
                if (range[0] != null) { where.add("d.paid_on >= ?"); params.add(range[0]); }
                if (range[1] != null) { where.add("d.paid_on <= ?"); params.add(range[1]); }
                select = """
                    select d.id as "_id", d.ref_no as ref, d.paid_on as "paidOn", donor.name as donor, donor.type as "_donorType",
                           n.name as need, owner.name as beneficiary, owner.type as "_ownerType", d.paid_amount as amount
                    """;
                fromSql = " from donation d join organisation donor on donor.id = d.donor_org_id join need n on n.id = d.need_id"
                        + " join organisation owner on owner.id = n.org_id";
                columns = DONATION_COLUMNS;
                orderBy = " order by d.paid_on desc, d.id desc";
            }
            case PURPOSES -> {
                if (scope.beneficiaryOrgId() != null) throw new NotAllowed("purposes are for staff and donors");
                if (scope.donorOrgId() != null) { where.add("p.donor_org_id = ?"); params.add(scope.donorOrgId()); }
                take(f, "state").ifPresent(s -> where.add(switch (s) {
                    case "MATCHED_DONATED" -> "exists (select 1 from donation d where d.purpose_id = p.id)";
                    case "MATCHED_NOT_DONATED" -> "exists (select 1 from match m where m.purpose_id = p.id) and not exists (select 1 from donation d where d.purpose_id = p.id)";
                    case "NOT_MATCHED" -> "not exists (select 1 from match m where m.purpose_id = p.id)";
                    default -> throw bad("Unknown purpose state " + s);
                }));
                take(f, "approved").ifPresent(v -> { where.add("p.approved = ?"); params.add(bool(v)); });
                select = """
                    select p.id as "_id", p.ref_no as ref, p.name, o.name as donor, p.sector, p.commitment_amount as commitment, p.periodicity,
                           (select count(*) from match m where m.purpose_id = p.id) as matches,
                           (select coalesce(sum(d.paid_amount),0) from donation d where d.purpose_id = p.id) as given
                    """;
                fromSql = " from purpose p join organisation o on o.id = p.donor_org_id";
                columns = PURPOSE_COLUMNS;
                orderBy = " order by p.name";
            }
            default -> throw bad("Unknown detail set " + set);
        }
        if (!f.isEmpty()) throw bad("Unknown filter " + String.join(", ", new TreeSet<>(f.keySet())));
        String whereSql = where.isEmpty() ? "" : " where " + String.join(" and ", where);
        String selectList = select.strip();
        return new Sql(selectList + fromSql + whereSql + orderBy + " limit " + MAX_ROWS, "select count(*)" + fromSql + whereSql, params, columns);
    }

    /** month=2026-10, quarter=Q2&fy=2026, or from/to (yyyy-mm-dd). */
    public static LocalDate[] period(Map<String, String> f) {
        Optional<String> month = take(f, "month"), quarter = take(f, "quarter"), fy = take(f, "fy"), from = take(f, "from"), to = take(f, "to");
        try {
            if (month.isPresent()) {
                YearMonth ym = YearMonth.parse(month.get());
                return new LocalDate[] { ym.atDay(1), ym.atEndOfMonth() };
            }
            if (quarter.isPresent()) {
                int q = switch (quarter.get()) { case "Q1" -> 1; case "Q2" -> 2; case "Q3" -> 3; case "Q4" -> 4; default -> throw bad("Unknown quarter " + quarter.get()); };
                int year = (int) number(fy.orElseThrow(() -> bad("A quarter needs fy (the year the financial year starts)")));
                LocalDate start = LocalDate.of(year, 4, 1).plusMonths(3L * (q - 1));
                return new LocalDate[] { start, start.plusMonths(3).minusDays(1) };
            }
            return new LocalDate[] { from.map(LocalDate::parse).orElse(null), to.map(LocalDate::parse).orElse(null) };
        } catch (DateTimeParseException e) {
            throw bad("Dates look like 2026-10-24 and months like 2026-10");
        }
    }

    public static String needState(String s) {
        String matched = "exists (select 1 from match m where m.need_id = n.id)";
        return switch (s) {
            case "MATCHED_DONATED" -> "n.approved and " + matched + " and n.raised_amount > 0";
            case "MATCHED_NO_DONATION" -> "n.approved and " + matched + " and n.raised_amount = 0";
            case "DONATED_NO_MATCH" -> "n.approved and not " + matched + " and n.raised_amount > 0";
            case "NEITHER" -> "n.approved and not " + matched + " and n.raised_amount = 0";
            case "RECEIVED" -> "n.raised_amount > 0";
            case "MATCHED_NOT_FUNDED" -> "n.raised_amount = 0 and " + matched;
            case "NO_MATCH" -> "n.raised_amount = 0 and not " + matched;
            case "WAITING_APPROVAL" -> "exists (select 1 from approval_request a where a.subject_type = 'NEED' and a.subject_id = n.id and a.status = 'SUBMITTED')";
            default -> throw bad("Unknown need state " + s);
        };
    }

    private static DetailColumn col(String key, String label) { return new DetailColumn(key, label, "text"); }
    private static Optional<String> take(Map<String, String> f, String key) {
        String v = f.remove(key);
        return v == null || v.isBlank() ? Optional.empty() : Optional.of(v.trim());
    }
    private static String allowed(String v, java.util.Set<String> allowed, String name) {
        if (!allowed.contains(v)) throw bad("Unknown " + name + " " + v);
        return v;
    }
    private static boolean bool(String v) {
        if (v.equals("true")) return true;
        if (v.equals("false")) return false;
        throw bad("Use true or false");
    }
    private static long number(String v) {
        try { return Long.parseLong(v); } catch (NumberFormatException e) { throw bad("Not a number: " + v); }
    }
    private static DomainException bad(String message) { return new DomainException("bad-filter", message); }
}
