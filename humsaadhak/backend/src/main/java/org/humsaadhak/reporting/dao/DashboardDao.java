package org.humsaadhak.reporting.dao;

import java.time.LocalDate;
import java.util.List;
import org.humsaadhak.reporting.util.BeneficiaryTerms.Flow;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Every query behind the role dashboards; DashboardService turns the results into charts. */
@Repository
public class DashboardDao {
    /** SQL twin of BeneficiaryTerms.fyQuarter: April-June is Q1, January-March is Q4. */
    private static final String FY_QUARTER = "('Q' || (((extract(month from d.paid_on)::int + 8) % 12) / 3 + 1))";

    public record MonthReceipts(String label, String month, double individualLakh, double corporateLakh) {}
    public record FundingStateCount(String bucket, double causes, double projects) {}
    public record QuarterTotals(String quarter, double first, double second) {}
    public record SectorTotal(String sector, double amount) {}
    public record NeedProgress(long id, String name, double raised, double target) {}

    private final JdbcClient db;

    public DashboardDao(JdbcClient db) { this.db = db; }

    // ---------------------------------------------------------------- platform
    public String organisationType(long orgId) { return db.sql("select type from organisation where id = ?").param(orgId).query(String.class).single(); }
    public long countDonationsSince(LocalDate from) { return count("select count(*) from donation where paid_on >= ?", from); }
    public double sumReceivedSince(LocalDate from) { return sum("select coalesce(sum(paid_amount),0) from donation where paid_on >= ?", from); }
    public long countApprovedNeeds(String kind) { return count("select count(*) from need where approved and kind = ?", kind); }
    public long countVerifiedOrganisations() { return count("select count(*) from organisation where verified"); }
    public long countOrganisations() { return count("select count(*) from organisation"); }
    public long countOrganisations(String type) { return count("select count(*) from organisation where type = ?", type); }
    public long countOrganisations(String type, boolean verified) { return count("select count(*) from organisation where type = ? and verified = ?", type, verified); }
    public long countUnverifiedDonors() { return count("select count(*) from organisation where type like 'DONOR_%' and not verified"); }
    public long countApprovedCandidates() { return count("select count(*) from match_candidate where decision = 'APPROVED'"); }
    public long countReviewedCandidates() { return count("select count(*) from match_candidate where decision <> 'PENDING'"); }

    public List<MonthReceipts> monthlyReceipts(LocalDate from) {
        return db.sql("""
            select to_char(date_trunc('month', d.paid_on), 'Mon'), to_char(date_trunc('month', d.paid_on), 'YYYY-MM'),
                   sum(case when o.type='DONOR_INDIVIDUAL' then d.paid_amount else 0 end)/100000.0,
                   sum(case when o.type='DONOR_CORPORATE' then d.paid_amount else 0 end)/100000.0
            from donation d join organisation o on o.id = d.donor_org_id
            where d.paid_on >= ? group by date_trunc('month', d.paid_on) order by date_trunc('month', d.paid_on)""")
                .param(from).query((rs, i) -> new MonthReceipts(rs.getString(1), rs.getString(2), rs.getDouble(3), rs.getDouble(4))).list();
    }

    public List<FundingStateCount> needFundingStates() {
        return db.sql("""
            select case when exists(select 1 from match m where m.need_id=n.id) and n.raised_amount>0 then 'MATCHED_DONATED'
                        when exists(select 1 from match m where m.need_id=n.id) then 'MATCHED_NO_DONATION'
                        when n.raised_amount>0 then 'DONATED_NO_MATCH'
                        else 'NEITHER' end as bucket,
                   sum(case when n.kind='CAUSE' then 1 else 0 end), sum(case when n.kind='PROJECT' then 1 else 0 end)
            from need n where n.approved group by 1""")
                .query((rs, i) -> new FundingStateCount(rs.getString(1), rs.getDouble(2), rs.getDouble(3))).list();
    }

    /** Donor type x need kind totals since the date, across the platform. */
    public List<Flow> flows(LocalDate from) {
        return db.sql("""
            select o.type, n.kind, sum(d.paid_amount) from donation d
            join organisation o on o.id = d.donor_org_id join need n on n.id = d.need_id
            where d.paid_on >= ? group by o.type, n.kind""")
                .param(from).query((rs, i) -> new Flow(rs.getString(1), rs.getString(2), rs.getDouble(3))).list();
    }

    // ---------------------------------------------------------------- operations associate
    public long countNeedApprovalsWaiting() { return count("select count(*) from approval_request where status='SUBMITTED' and subject_type='NEED'"); }
    public long countRequestsByMaker(long userId, String status) { return count("select count(*) from approval_request where maker_id=? and status=?", userId, status); }
    public long countApprovedThisMonthBy(long userId) {
        return count("select count(*) from approval_request where maker_id=? and status='APPROVED' and updated_at >= date_trunc('month', now())", userId);
    }

    // ---------------------------------------------------------------- donor
    public double sumGivenSince(long donorOrgId, LocalDate from) { return sum("select coalesce(sum(paid_amount),0) from donation where donor_org_id=? and paid_on >= ?", donorOrgId, from); }
    public double sumPledged(long donorOrgId) { return sum("select coalesce(sum(commitment_amount),0) from purpose where donor_org_id=? and approved", donorOrgId); }
    public long countBeneficiariesSupported(long donorOrgId, String needKind) {
        return count("select count(distinct n.org_id) from donation d join need n on n.id=d.need_id where d.donor_org_id=? and n.kind=?", donorOrgId, needKind);
    }
    public double sumGivenToKindSince(long donorOrgId, String needKind, LocalDate from) {
        return sum("select coalesce(sum(d.paid_amount),0) from donation d join need n on n.id=d.need_id where d.donor_org_id=? and n.kind=? and d.paid_on>=?",
                donorOrgId, needKind, from);
    }
    public long countPurposesMatchedAndDonated(long donorOrgId) {
        return count("select count(*) from purpose p where p.donor_org_id=? and exists(select 1 from donation d where d.purpose_id=p.id)", donorOrgId);
    }
    public long countPurposesMatchedNotDonated(long donorOrgId) {
        return count("select count(*) from purpose p where p.donor_org_id=? and exists(select 1 from match m where m.purpose_id=p.id)"
                + " and not exists(select 1 from donation d where d.purpose_id=p.id)", donorOrgId);
    }
    public long countPurposesNotMatched(long donorOrgId) {
        return count("select count(*) from purpose p where p.donor_org_id=? and not exists(select 1 from match m where m.purpose_id=p.id)", donorOrgId);
    }
    /** Given to causes (first) and projects (second) per financial quarter. */
    public List<QuarterTotals> donorQuarterTotals(long donorOrgId, LocalDate from) {
        return quarters("""
            select %s as q, sum(case when n.kind='CAUSE' then d.paid_amount else 0 end), sum(case when n.kind='PROJECT' then d.paid_amount else 0 end)
            from donation d join need n on n.id=d.need_id where d.donor_org_id=? and d.paid_on>=? group by 1""".formatted(FY_QUARTER), donorOrgId, from);
    }
    public List<SectorTotal> givingBySector(long donorOrgId, LocalDate from) {
        return db.sql("select n.sector, sum(d.paid_amount) from donation d join need n on n.id=d.need_id where d.donor_org_id=? and d.paid_on>=? group by n.sector order by 2 desc")
                .params(donorOrgId, from).query((rs, i) -> new SectorTotal(rs.getString(1), rs.getDouble(2))).list();
    }
    public long countDonationsBySince(long donorOrgId, LocalDate from) { return count("select count(*) from donation where donor_org_id=? and paid_on>=?", donorOrgId, from); }
    public long countApprovedPurposes(long donorOrgId) { return count("select count(*) from purpose where donor_org_id=? and approved", donorOrgId); }

    // ---------------------------------------------------------------- school or NGO
    public double sumRaised(long orgId) { return sum("select coalesce(sum(raised_amount),0) from need where org_id=?", orgId); }
    public double sumApprovedTarget(long orgId) { return sum("select coalesce(sum(target_amount),0) from need where org_id=? and approved", orgId); }
    public long countNeedsWithDonation(long orgId) { return count("select count(*) from need where org_id=? and raised_amount>0", orgId); }
    public long countNeedsMatchedNotFunded(long orgId) {
        return count("select count(*) from need n where n.org_id=? and n.raised_amount=0 and exists(select 1 from match m where m.need_id=n.id)", orgId);
    }
    public long countNeedsWithoutMatch(long orgId) {
        return count("select count(*) from need n where n.org_id=? and n.raised_amount=0 and not exists(select 1 from match m where m.need_id=n.id)", orgId);
    }
    public double sumReceivedFrom(long orgId, String donorType) {
        return sum("select coalesce(sum(d.paid_amount),0) from donation d join need n on n.id=d.need_id join organisation o on o.id=d.donor_org_id"
                + " where n.org_id=? and o.type=?", orgId, donorType);
    }
    public long countDonors(long orgId, String donorType) {
        return count("select count(distinct d.donor_org_id) from donation d join need n on n.id=d.need_id join organisation o on o.id=d.donor_org_id"
                + " where n.org_id=? and o.type=?", orgId, donorType);
    }
    public List<NeedProgress> needProgress(long orgId) {
        return db.sql("select id, name, raised_amount, target_amount from need where org_id=? and approved order by name")
                .param(orgId).query((rs, i) -> new NeedProgress(rs.getLong(1), rs.getString(2), rs.getDouble(3), rs.getDouble(4))).list();
    }
    /** Received from individual (first) and corporate (second) donors per financial quarter. */
    public List<QuarterTotals> beneficiaryQuarterTotals(long orgId, LocalDate from) {
        return quarters("""
            select %s as q, sum(case when o.type='DONOR_INDIVIDUAL' then d.paid_amount else 0 end), sum(case when o.type='DONOR_CORPORATE' then d.paid_amount else 0 end)
            from donation d join need n on n.id=d.need_id join organisation o on o.id=d.donor_org_id
            where n.org_id=? and d.paid_on>=? group by 1""".formatted(FY_QUARTER), orgId, from);
    }
    public long countShareRequestsWaiting(long orgId) { return count("select count(*) from share_request where owner_org_id=? and status='REQUESTED'", orgId); }

    // ---------------------------------------------------------------- helpers
    private List<QuarterTotals> quarters(String sql, Object... params) {
        return db.sql(sql).params(params).query((rs, i) -> new QuarterTotals(rs.getString(1), rs.getDouble(2), rs.getDouble(3))).list();
    }
    private long count(String sql, Object... params) { return db.sql(sql).params(params).query(Long.class).single(); }
    private double sum(String sql, Object... params) { return db.sql(sql).params(params).query(Double.class).single(); }
}
