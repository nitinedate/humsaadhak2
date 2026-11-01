package org.humsaadhak.identity.dao;

import java.util.Collection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Spring Session's store (tables from V6): sessions are indexed by the signed-in email in principal_name. */
@Repository
public class SessionDao {
    private final JdbcClient db;

    public SessionDao(JdbcClient db) { this.db = db; }

    /** Deletes every session of these people (their attributes go with them, on delete cascade). */
    public int deleteSessionsOf(Collection<String> emails) {
        return db.sql("delete from spring_session where lower(principal_name) = any(?)")
                .param(emails.stream().map(String::toLowerCase).toArray(String[]::new)).update();
    }
}
