package org.humsaadhak.reporting.dao;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Counts of work waiting, for the side-panel badges. */
@Repository
public class CountersDao {
    private final JdbcClient db;

    public CountersDao(JdbcClient db) { this.db = db; }

    /** Submitted approvals the person did not make themselves (maker ≠ checker). */
    public long approvalsWaitingFor(long checkerId) {
        return db.sql("select count(*) from approval_request where status = 'SUBMITTED' and maker_id <> ?").param(checkerId).query(Long.class).single();
    }

    public long documentsWaitingForReview() {
        return db.sql("select count(*) from document where state = 'PENDING' and not superseded").query(Long.class).single();
    }

    public long shareRequestsWaitingFor(long ownerOrgId) {
        return db.sql("select count(*) from share_request where owner_org_id = ? and status = 'REQUESTED'").param(ownerOrgId).query(Long.class).single();
    }
}
