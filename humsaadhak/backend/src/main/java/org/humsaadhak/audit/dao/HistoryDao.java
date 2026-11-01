package org.humsaadhak.audit.dao;

import java.util.List;
import java.util.Map;
import org.humsaadhak.audit.model.AuditedType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads Envers revisions of one record, oldest first, with who made each change. */
@Repository
public class HistoryDao {
    private final JdbcClient db;

    public HistoryDao(JdbcClient db) { this.db = db; }

    public List<Map<String, Object>> revisions(AuditedType type, long id) {
        // the table name comes from the AuditedType allow-list, never from the request
        return db.sql("select a.*, r.revtstmp, r.username, r.request_id from " + type.table() + " a join revinfo r on r.rev = a.rev where a.id = ? order by a.rev")
                .param(id).query().listOfRows();
    }
}
