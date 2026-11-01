package org.humsaadhak.reporting.dao;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Runs chart-detail queries (built and scoped by DetailQuery) and loads the expandable child rows. */
@Repository
public class DetailDao {
    private final JdbcClient db;

    public DetailDao(JdbcClient db) { this.db = db; }

    public long count(DetailQuery.Sql q) { return db.sql(q.countSql()).params(q.params()).query(Long.class).single(); }

    public List<Map<String, Object>> rows(DetailQuery.Sql q) { return db.sql(q.select()).params(q.params()).query().listOfRows(); }

    /** Causes and projects of these organisations; "owner" is the organisation id. */
    public List<Map<String, Object>> needsOf(Long[] orgIds) {
        return db.sql("select org_id as owner, ref_no as ref, name, target_amount as target, raised_amount as raised from need where org_id = any(?) order by name")
                .param(orgIds).query().listOfRows();
    }

    /** Donations made by these donor organisations; "owner" is the donor organisation id. */
    public List<Map<String, Object>> donationsBy(Long[] donorOrgIds) {
        return db.sql("""
                select d.donor_org_id as owner, d.ref_no as ref, d.paid_on as "paidOn", o.name as party, n.name as need, d.paid_amount as amount
                from donation d join need n on n.id = d.need_id join organisation o on o.id = n.org_id
                where d.donor_org_id = any(?) order by d.paid_on desc""").param(donorOrgIds).query().listOfRows();
    }

    /** Donations received by these causes and projects; "owner" is the need id. */
    public List<Map<String, Object>> donationsTo(Long[] needIds) {
        return db.sql("""
                select d.need_id as owner, d.ref_no as ref, d.paid_on as "paidOn", o.name as party, n.name as need, d.paid_amount as amount
                from donation d join organisation o on o.id = d.donor_org_id join need n on n.id = d.need_id
                where d.need_id = any(?) order by d.paid_on desc""").param(needIds).query().listOfRows();
    }

    /** Confirmed matches of these purposes in priority order; "owner" is the purpose id. */
    public List<Map<String, Object>> matchesOf(Long[] purposeIds) {
        return db.sql("""
                select m.purpose_id as owner, m.priority, m.ref_no as ref, n.name as need, o.name as beneficiary,
                       greatest(n.target_amount - n.raised_amount, 0) as remaining
                from match m join need n on n.id = m.need_id join organisation o on o.id = n.org_id
                where m.purpose_id = any(?) order by m.purpose_id, m.priority""").param(purposeIds).query().listOfRows();
    }
}
