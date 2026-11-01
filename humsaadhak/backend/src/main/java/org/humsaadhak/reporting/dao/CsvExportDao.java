package org.humsaadhak.reporting.dao;

import java.time.LocalDate;
import java.util.List;
import org.humsaadhak.reporting.dto.DonationExportRow;
import org.humsaadhak.reporting.dto.NeedExportRow;
import org.humsaadhak.reporting.dto.OrganisationExportRow;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Whole-table exports for staff. Fixed, parameterised queries only: this replaces the legacy report builder, whose
 * user-editable SQL fragments were an injection risk (S7). Bank details are never selected.
 */
@Repository
public class CsvExportDao {
    private final JdbcClient db;

    public CsvExportDao(JdbcClient db) { this.db = db; }

    public List<DonationExportRow> donations(LocalDate from, LocalDate to) {
        return db.sql("""
            select d.ref_no, d.paid_on, donor.name, donor.type, n.name, owner.name, owner.type, d.paid_amount, d.pledged_amount, d.payment_ref,
                   d.match_id is not null
            from donation d join organisation donor on donor.id = d.donor_org_id join need n on n.id = d.need_id
            join organisation owner on owner.id = n.org_id where d.paid_on between ? and ? order by d.paid_on, d.id""")
                .params(from, to)
                .query((rs, i) -> new DonationExportRow(rs.getString(1), rs.getDate(2).toLocalDate(), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7), rs.getBigDecimal(8), rs.getBigDecimal(9), rs.getString(10), rs.getBoolean(11)))
                .list();
    }

    public List<OrganisationExportRow> organisations() {
        return db.sql("select ref_no, name, type, city, state, verified, created_at::date from organisation order by type, name")
                .query((rs, i) -> new OrganisationExportRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getBoolean(6), rs.getDate(7) == null ? null : rs.getDate(7).toLocalDate()))
                .list();
    }

    public List<NeedExportRow> needs() {
        return db.sql("""
            select n.ref_no, n.name, n.kind, o.name, o.type, n.sector, n.target_amount, n.raised_amount,
                   greatest(n.target_amount - n.raised_amount, 0), n.approved, n.completed
            from need n join organisation o on o.id = n.org_id order by o.type, o.name, n.name""")
                .query((rs, i) -> new NeedExportRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getBigDecimal(9), rs.getBoolean(10), rs.getBoolean(11)))
                .list();
    }
}
