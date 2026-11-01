package org.humsaadhak.audit.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.humsaadhak.audit.dto.AuditEntry;
import org.humsaadhak.audit.dto.AuditFilter;
import org.humsaadhak.audit.dto.NewAuditEvent;
import org.humsaadhak.audit.util.AuditActions;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The audit_event table: append-only (a database trigger refuses updates and deletes). Every query is fixed; filters
 * only add bound parameters.
 */
@Repository
public class AuditDao {
    static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final String COLUMNS = "id, at, actor_name, actor_email, actor_role, action, category, target_type, target_id, outcome, "
            + "error_code, detail, ip, user_agent, request_id";
    private final JdbcClient db;

    public AuditDao(JdbcClient db) { this.db = db; }

    public void insert(NewAuditEvent e) {
        String orgs = e.orgIds() == null || e.orgIds().isEmpty() ? "{}"
                : e.orgIds().stream().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
        db.sql("""
                insert into audit_event (request_id, actor_id, actor_email, actor_name, actor_role, actor_org_id, action, category,
                                         target_type, target_id, org_ids, outcome, error_code, detail, ip, user_agent)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::bigint[], ?, ?, ?, ?, ?)""")
                .params(e.requestId(), e.actorId(), e.actorEmail(), e.actorName(), e.actorRole(), e.actorOrgId(), e.action(), e.category(),
                        e.targetType(), e.targetId(), orgs, e.outcome(), e.errorCode(), e.detail(), e.ip(), e.userAgent())
                .update();
    }

    /** Every recorded action on one record, newest first (at most 500). */
    public List<AuditEntry> forTarget(String type, String id, boolean includeFailures) {
        return db.sql("select " + COLUMNS + " from audit_event where target_type = ? and target_id = ?"
                        + (includeFailures ? "" : " and outcome = 'SUCCESS'") + " order by at desc, id desc limit 500")
                .params(type, id).query(AuditDao::map).list();
    }

    /**
     * Actions in some categories: everything for staff (org null), or what concerns one organisation, or one person's own
     * actions (actorId set).
     */
    public List<AuditEntry> activity(List<String> categories, Long org, Long actorId, boolean includeFailures, int limit, int offset) {
        Where w = activityWhere(categories, org, actorId, includeFailures);
        return db.sql("select " + COLUMNS + " from audit_event" + w.sql + " order by at desc, id desc limit ? offset ?")
                .params(w.with(limit, offset)).query(AuditDao::map).list();
    }

    public long countActivity(List<String> categories, Long org, Long actorId, boolean includeFailures) {
        Where w = activityWhere(categories, org, actorId, includeFailures);
        return db.sql("select count(*) from audit_event" + w.sql).params(w.params).query(Long.class).single();
    }

    public List<AuditEntry> search(AuditFilter f, int limit, int offset) {
        Where w = searchWhere(f);
        return db.sql("select " + COLUMNS + " from audit_event" + w.sql + " order by at desc, id desc limit ? offset ?")
                .params(w.with(limit, offset)).query(AuditDao::map).list();
    }

    public long countSearch(AuditFilter f) {
        Where w = searchWhere(f);
        return db.sql("select count(*) from audit_event" + w.sql).params(w.params).query(Long.class).single();
    }

    private record Where(String sql, List<Object> params) {
        List<Object> with(Object... more) { List<Object> all = new ArrayList<>(params); all.addAll(List.of(more)); return all; }
    }

    private static Where activityWhere(List<String> categories, Long org, Long actorId, boolean includeFailures) {
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        clauses.add("category in (" + String.join(",", categories.stream().map(c -> "?").toList()) + ")");
        params.addAll(categories);
        if (actorId != null) { clauses.add("actor_id = ?"); params.add(actorId); }
        if (org != null) { clauses.add("(? = any(org_ids) or actor_org_id = ?)"); params.add(org); params.add(org); }
        if (!includeFailures) clauses.add("outcome = 'SUCCESS'");
        return new Where(" where " + String.join(" and ", clauses), params);
    }

    private static Where searchWhere(AuditFilter f) {
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (f.from() != null) { clauses.add("at >= ?"); params.add(java.sql.Timestamp.from(f.from().atStartOfDay(INDIA).toInstant())); }
        if (f.to() != null) { clauses.add("at < ?"); params.add(java.sql.Timestamp.from(f.to().plusDays(1).atStartOfDay(INDIA).toInstant())); }
        if (f.actor() != null && !f.actor().isBlank()) {
            clauses.add("(lower(actor_email) like ? or lower(actor_name) like ?)");
            String like = "%" + f.actor().strip().toLowerCase().replace("%", "").replace("_", "") + "%";
            params.add(like); params.add(like);
        }
        if (f.category() != null && !f.category().isBlank()) { clauses.add("category = ?"); params.add(f.category()); }
        if (f.outcome() != null && !f.outcome().isBlank()) { clauses.add("outcome = ?"); params.add(f.outcome()); }
        if (f.targetType() != null && !f.targetType().isBlank()) { clauses.add("target_type = ?"); params.add(f.targetType()); }
        if (f.targetId() != null && !f.targetId().isBlank()) { clauses.add("target_id = ?"); params.add(f.targetId().strip()); }
        return new Where(clauses.isEmpty() ? "" : " where " + String.join(" and ", clauses), params);
    }

    static AuditEntry map(ResultSet rs, int row) throws SQLException {
        String action = rs.getString("action");
        return new AuditEntry(rs.getLong("id"), rs.getTimestamp("at").toInstant(), rs.getString("actor_name"), rs.getString("actor_email"),
                rs.getString("actor_role"), action, AuditActions.label(action), rs.getString("category"), rs.getString("target_type"),
                rs.getString("target_id"), rs.getString("outcome"), rs.getString("error_code"), rs.getString("detail"), rs.getString("ip"),
                rs.getString("user_agent"), rs.getString("request_id"), List.of());
    }
}
