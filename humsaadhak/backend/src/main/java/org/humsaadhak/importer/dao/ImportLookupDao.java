package org.humsaadhak.importer.dao;

import java.util.HashSet;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reference values the importer validates rows against. */
@Repository
public class ImportLookupDao {
    private final JdbcClient db;

    public ImportLookupDao(JdbcClient db) { this.db = db; }

    public Set<String> sectorNames() { return new HashSet<>(db.sql("select name from sector").query(String.class).list()); }
}
