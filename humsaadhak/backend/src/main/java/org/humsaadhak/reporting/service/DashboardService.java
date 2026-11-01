package org.humsaadhak.reporting.service;

import static org.humsaadhak.reporting.util.BeneficiaryTerms.*;
import static org.humsaadhak.reporting.util.ReportFormat.*;

import java.time.LocalDate;
import java.util.List;
import org.humsaadhak.reporting.dao.DashboardDao;
import org.humsaadhak.reporting.dao.DashboardDao.QuarterTotals;
import org.humsaadhak.reporting.dto.Bar;
import org.humsaadhak.reporting.dto.BarChart;
import org.humsaadhak.reporting.dto.Dashboard;
import org.humsaadhak.reporting.dto.Donut;
import org.humsaadhak.reporting.dto.Drill;
import org.humsaadhak.reporting.dto.Kpi;
import org.humsaadhak.reporting.dto.Slice;
import org.humsaadhak.reporting.util.ReportFormat;
import org.humsaadhak.shared.security.CurrentUser;
import org.humsaadhak.shared.security.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The four-group dashboards (individual donors, corporate donors, direct beneficiaries = schools, indirect
 * beneficiaries = NGOs). Every slice and bar carries a Drill key that opens the records behind it.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {
    private final DashboardDao data;

    public DashboardService(DashboardDao data) { this.data = data; }

    /** Kept for callers that used it here; the helper lives in ReportFormat. */
    public static LocalDate fyStart(LocalDate d) { return ReportFormat.fyStart(d); }

    /** The server decides which dashboard the person may see. */
    public Dashboard forUser(CurrentUser me, LocalDate today) {
        if (me.role() == Role.MANAGER || me.role() == Role.SUPER_ADMIN) return manager(today);
        if (me.role() == Role.ASSOCIATE) return associate(me.id());
        if (me.role().isDonor()) return donor(me.orgId(), today);
        return beneficiary(me.orgId(), data.organisationType(me.orgId()), today);
    }

    // ------------------------------------------------------------------ manager and super admin
    public Dashboard manager(LocalDate today) {
        LocalDate fy = fyStart(today);
        String from = fy.toString();
        long causes = data.countApprovedNeeds("CAUSE"), projects = data.countApprovedNeeds("PROJECT");

        Donut beneficiaries = new Donut("Beneficiaries by verification", "beneficiaries", List.of(
                orgSlice(DIRECT + ", verified", "rust", "SCHOOL", true), orgSlice(DIRECT + ", waiting", "rust-light", "SCHOOL", false),
                orgSlice(INDIRECT + ", verified", "indigo", "NGO", true), orgSlice(INDIRECT + ", waiting", "indigo-light", "NGO", false)));
        Donut donors = new Donut("Donors by verification", "donors", List.of(
                orgSlice(INDIVIDUAL + ", verified", "rust", "DONOR_INDIVIDUAL", true), orgSlice(CORPORATE + ", verified", "indigo", "DONOR_CORPORATE", true),
                new Slice("Waiting for approval", data.countUnverifiedDonors(), "grey", Drill.of("ORGANISATIONS", "group", "DONORS", "verified", "false"))));

        List<Bar> months = data.monthlyReceipts(fy).stream().map(m -> new Bar(m.label(), List.of(m.individualLakh(), m.corporateLakh()), List.of(
                Drill.of("DONATIONS", "month", m.month(), "donorType", "DONOR_INDIVIDUAL"),
                Drill.of("DONATIONS", "month", m.month(), "donorType", "DONOR_CORPORATE")))).toList();

        var counts = data.needFundingStates();
        List<Bar> stateBars = List.of(
                new String[] { "MATCHED_DONATED", "Matched and donated" }, new String[] { "MATCHED_NO_DONATION", "Matched, no donation yet" },
                new String[] { "DONATED_NO_MATCH", "Donated without a match" }, new String[] { "NEITHER", "Neither matched nor donated" })
                .stream().map(b -> {
                    var row = counts.stream().filter(r -> r.bucket().equals(b[0])).findFirst().orElse(new DashboardDao.FundingStateCount(b[0], 0, 0));
                    return new Bar(b[1], List.of(row.causes(), row.projects()),
                            List.of(Drill.of("NEEDS", "state", b[0], "kind", "CAUSE"), Drill.of("NEEDS", "state", b[0], "kind", "PROJECT")));
                }).toList();

        return new Dashboard("MANAGER", "Manager dashboard", List.of(
                new Kpi("Donations", String.valueOf(data.countDonationsSince(fy)), lakh(data.sumReceivedSince(fy)) + " received this financial year"),
                new Kpi("Approved causes and projects", String.valueOf(causes + projects), causes + " for direct, " + projects + " for indirect beneficiaries"),
                new Kpi("Verified organisations", String.valueOf(data.countVerifiedOrganisations()), "of " + data.countOrganisations() + " registered"),
                new Kpi("Match approval rate", pct(data.countApprovedCandidates(), data.countReviewedCandidates()), "of candidates reviewed")),
                List.of(organisationsByType("Organisations by type"), beneficiaries, donors),
                List.of(new BarChart("Money received by month", List.of("From individual donors", "From corporate donors"), "₹ lakh", months),
                        new BarChart("Who gave to whom", List.of("To " + lower(DIRECT), "To " + lower(INDIRECT)), "₹", whoGaveToWhom(data.flows(fy), from)),
                        new BarChart("Causes and projects by funding state", List.of(DIRECT, INDIRECT), "count", stateBars)));
    }

    // ------------------------------------------------------------------ operations associate
    public Dashboard associate(long userId) {
        Donut listed = new Donut("Causes and projects on the platform", "listed", List.of(
                new Slice("Causes of " + lower(DIRECT), data.countApprovedNeeds("CAUSE"), "rust", Drill.of("NEEDS", "kind", "CAUSE", "approved", "true")),
                new Slice("Projects of " + lower(INDIRECT), data.countApprovedNeeds("PROJECT"), "indigo", Drill.of("NEEDS", "kind", "PROJECT", "approved", "true")),
                new Slice("Waiting for approval", data.countNeedApprovalsWaiting(), "grey", Drill.of("NEEDS", "state", "WAITING_APPROVAL"))));
        return new Dashboard("ASSOCIATE", "Your work", List.of(
                new Kpi("Returned to you", String.valueOf(data.countRequestsByMaker(userId, "REJECTED")), "fix and resubmit first"),
                new Kpi("Drafts", String.valueOf(data.countRequestsByMaker(userId, "DRAFT")), "not yet submitted"),
                new Kpi("Waiting for a manager", String.valueOf(data.countRequestsByMaker(userId, "SUBMITTED")), ""),
                new Kpi("Approved this month", String.valueOf(data.countApprovedThisMonthBy(userId)), "")),
                List.of(listed, organisationsByType("Organisations on the platform")), List.of());
    }

    // ------------------------------------------------------------------ individual or corporate donor
    public Dashboard donor(long orgId, LocalDate today) {
        LocalDate fy = fyStart(today);
        String from = fy.toString();
        double given = data.sumGivenSince(orgId, fy), pledged = data.sumPledged(orgId);
        long direct = data.countBeneficiariesSupported(orgId, "CAUSE"), indirect = data.countBeneficiariesSupported(orgId, "PROJECT");

        Donut where = new Donut("Where your money went", "given this year", List.of(
                new Slice(DIRECT, data.sumGivenToKindSince(orgId, "CAUSE", fy), "rust", Drill.of("DONATIONS", "needKind", "CAUSE", "from", from)),
                new Slice(INDIRECT, data.sumGivenToKindSince(orgId, "PROJECT", fy), "indigo", Drill.of("DONATIONS", "needKind", "PROJECT", "from", from))));
        Donut purposes = new Donut("Your purposes", "purposes", List.of(
                new Slice("Matched and donated", data.countPurposesMatchedAndDonated(orgId), "green", Drill.of("PURPOSES", "state", "MATCHED_DONATED")),
                new Slice("Matched, not donated yet", data.countPurposesMatchedNotDonated(orgId), "amber", Drill.of("PURPOSES", "state", "MATCHED_NOT_DONATED")),
                new Slice("Not matched yet", data.countPurposesNotMatched(orgId), "grey", Drill.of("PURPOSES", "state", "NOT_MATCHED"))));
        List<Bar> sectors = data.givingBySector(orgId, fy).stream().map(s -> new Bar(s.sector(), List.of(s.amount()),
                List.of(Drill.of("DONATIONS", "sector", s.sector(), "from", from)))).toList();

        return new Dashboard("DONOR", "Your giving at a glance", List.of(
                new Kpi("Given", rupees(given), pct((long) given, (long) pledged) + " of " + rupees(pledged) + " pledged"),
                new Kpi("Beneficiaries supported", String.valueOf(direct + indirect), supportedNote(direct, indirect)),
                new Kpi("Donations made", String.valueOf(data.countDonationsBySince(orgId, fy)), "this financial year"),
                new Kpi("Commitments", String.valueOf(data.countApprovedPurposes(orgId)), "approved purposes")),
                List.of(where, purposes),
                List.of(new BarChart("Given to direct and indirect beneficiaries, by quarter", List.of(DIRECT, INDIRECT), "₹",
                                quarterBars(data.donorQuarterTotals(orgId, fy), fy, "needKind", "CAUSE", "PROJECT")),
                        new BarChart("Giving by sector", List.of("Given"), "₹", sectors)));
    }

    // ------------------------------------------------------------------ direct (school) or indirect (NGO) beneficiary
    public Dashboard beneficiary(long orgId, String orgType, LocalDate today) {
        LocalDate fy = fyStart(today);
        double raised = data.sumRaised(orgId), target = data.sumApprovedTarget(orgId);
        boolean school = "SCHOOL".equals(orgType);
        Donut state = new Donut((school ? "Causes" : "Projects") + " by funding state", school ? "causes" : "projects", List.of(
                new Slice("Received a donation", data.countNeedsWithDonation(orgId), "green", Drill.of("NEEDS", "state", "RECEIVED")),
                new Slice("Matched, not funded yet", data.countNeedsMatchedNotFunded(orgId), "amber", Drill.of("NEEDS", "state", "MATCHED_NOT_FUNDED")),
                new Slice("No match yet", data.countNeedsWithoutMatch(orgId), "grey", Drill.of("NEEDS", "state", "NO_MATCH"))));
        Donut donors = new Donut("Money by donor type", "received", List.of(
                new Slice(INDIVIDUAL, data.sumReceivedFrom(orgId, "DONOR_INDIVIDUAL"), "rust", Drill.of("DONATIONS", "donorType", "DONOR_INDIVIDUAL")),
                new Slice(CORPORATE, data.sumReceivedFrom(orgId, "DONOR_CORPORATE"), "indigo", Drill.of("DONATIONS", "donorType", "DONOR_CORPORATE"))));
        long corporate = data.countDonors(orgId, "DONOR_CORPORATE"), individual = data.countDonors(orgId, "DONOR_INDIVIDUAL");
        List<Bar> progress = data.needProgress(orgId).stream().map(n -> {
            Drill gifts = Drill.of("DONATIONS", "needId", String.valueOf(n.id()));
            return new Bar(n.name(), List.of(n.raised(), n.target()), List.of(gifts, gifts));
        }).toList();

        return new Dashboard(school ? "SCHOOL" : "NGO", beneficiaryTitle(orgType),
                List.of(new Kpi("Received", rupees(raised), pct((long) raised, (long) target) + " of " + rupees(target)),
                        new Kpi("Donors", String.valueOf(corporate + individual), donorMixNote(corporate, individual)),
                        new Kpi("Document requests", String.valueOf(data.countShareRequestsWaiting(orgId)), "waiting for your answer")),
                List.of(state, donors),
                List.of(new BarChart("Raised against target", List.of("Raised", "Target"), "₹", progress),
                        new BarChart("Received from individual and corporate donors, by quarter", List.of(INDIVIDUAL, CORPORATE), "₹",
                                quarterBars(data.beneficiaryQuarterTotals(orgId, fy), fy, "donorType", "DONOR_INDIVIDUAL", "DONOR_CORPORATE"))));
    }

    // ------------------------------------------------------------------ helpers
    private Donut organisationsByType(String title) {
        return new Donut(title, "organisations", List.of(typeSlice(INDIVIDUAL, "rust", "DONOR_INDIVIDUAL"), typeSlice(CORPORATE, "indigo", "DONOR_CORPORATE"),
                typeSlice(DIRECT, "amber", "SCHOOL"), typeSlice(INDIRECT, "teal", "NGO")));
    }

    private Slice typeSlice(String label, String color, String type) {
        return new Slice(label, data.countOrganisations(type), color, Drill.of("ORGANISATIONS", "type", type));
    }

    private Slice orgSlice(String label, String color, String type, boolean verified) {
        return new Slice(label, data.countOrganisations(type, verified), color, Drill.of("ORGANISATIONS", "type", type, "verified", String.valueOf(verified)));
    }

    /**
     * Always Q1..Q4 of the financial year (April-June is Q1), zero-filled; series i of each bar drills into
     * donations of that quarter filtered by filterKey=values[i].
     */
    private static List<Bar> quarterBars(List<QuarterTotals> rows, LocalDate fy, String filterKey, String first, String second) {
        String year = String.valueOf(fy.getYear());
        return List.of("Q1", "Q2", "Q3", "Q4").stream().map(q -> {
            List<Double> values = rows.stream().filter(r -> r.quarter().equals(q)).findFirst().map(r -> List.of(r.first(), r.second())).orElse(List.of(0.0, 0.0));
            return new Bar(q, values, List.of(Drill.of("DONATIONS", "quarter", q, "fy", year, filterKey, first),
                    Drill.of("DONATIONS", "quarter", q, "fy", year, filterKey, second)));
        }).toList();
    }
}
