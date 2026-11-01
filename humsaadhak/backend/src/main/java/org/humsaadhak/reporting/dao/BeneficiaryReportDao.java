package org.humsaadhak.reporting.dao;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.humsaadhak.reporting.dto.NeedOption;
import org.humsaadhak.reporting.dto.ReportOrganisation;
import org.humsaadhak.reporting.util.BeneficiaryTerms;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Data for the original project's beneficiary reports; the report SQL itself is built by BeneficiaryReportQuery. */
@Repository
public class BeneficiaryReportDao {
    private final JdbcClient db;

    public BeneficiaryReportDao(JdbcClient db) { this.db = db; }

    public Optional<ReportOrganisation> organisation(long orgId) {
        return db.sql("select id, name, ref_no, type from organisation where id = ?").param(orgId)
                .query((rs, i) -> new ReportOrganisation(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), BeneficiaryTerms.singular(rs.getString(4))))
                .optional();
    }

    public List<NeedOption> needOptions(long orgId) {
        return db.sql("select id, ref_no, name, approved from need where org_id = ? order by name").param(orgId)
                .query((rs, i) -> new NeedOption(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4))).list();
    }

    /** The first day the organisation had a cause or project (approved or created), for the financial-year list. */
    public LocalDate firstActivity(long orgId) {
        return db.sql("select min(coalesce(approved_at, created_at))::date from need where org_id = ?").param(orgId).query(LocalDate.class).optional().orElse(null);
    }

    public boolean needBelongsTo(long needId, long orgId) {
        return db.sql("select count(*) from need where id = ? and org_id = ?").params(needId, orgId).query(Long.class).single() > 0;
    }

    public String needName(long needId) { return db.sql("select name from need where id = ?").param(needId).query(String.class).single(); }

    public long count(BeneficiaryReportQuery.Query q) { return db.sql(q.countSql()).params(q.params()).query(Long.class).single(); }

    public List<Map<String, Object>> rows(BeneficiaryReportQuery.Query q, int limit, long offset) {
        List<Object> params = new ArrayList<>(q.params());
        params.add(limit);
        params.add(offset);
        return db.sql(q.select()).params(params).query().listOfRows();
    }
}
