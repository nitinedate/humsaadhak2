package org.humsaadhak.reporting.dao;

import java.util.List;
import org.humsaadhak.reporting.dto.OpenNeed;
import org.humsaadhak.reporting.dto.OrganisationOption;
import org.humsaadhak.reporting.util.BeneficiaryTerms;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Read models for form pickers. */
@Repository
public class LookupDao {
    /** Organisation groups a picker can ask for, with their fixed SQL condition (never built from the request). */
    public enum Group {
        DONORS("type like 'DONOR_%'"), BENEFICIARIES("type in ('SCHOOL','NGO')");
        private final String condition;
        Group(String condition) { this.condition = condition; }
    }

    private final JdbcClient db;

    public LookupDao(JdbcClient db) { this.db = db; }

    /** Approved causes and projects that still need money, matching the search text (name, beneficiary or reference). */
    public List<OpenNeed> openNeeds(String like) {
        return db.sql("""
                select n.id, n.ref_no, n.name, n.kind, n.sector, o.id, o.name, o.type, n.target_amount, n.raised_amount,
                       greatest(n.target_amount - n.raised_amount, 0)
                from need n join organisation o on o.id = n.org_id
                where n.approved and not n.completed and n.raised_amount < n.target_amount
                  and (lower(n.name) like ? or lower(o.name) like ? or lower(coalesce(n.ref_no,'')) like ?)
                order by o.name, n.name limit 200""")
                .params(like, like, like)
                .query((rs, i) -> new OpenNeed(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getLong(6),
                        rs.getString(7), BeneficiaryTerms.singular(rs.getString(8)), rs.getBigDecimal(9), rs.getBigDecimal(10), rs.getBigDecimal(11)))
                .list();
    }

    public List<OrganisationOption> organisations(Group group, String like) {
        return db.sql("select id, ref_no, name, type, verified from organisation where " + group.condition
                        + " and (lower(name) like ? or lower(coalesce(ref_no,'')) like ?) order by name limit 500")
                .params(like, like)
                .query((rs, i) -> new OrganisationOption(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        BeneficiaryTerms.singular(rs.getString(4)), rs.getBoolean(5)))
                .list();
    }
}
