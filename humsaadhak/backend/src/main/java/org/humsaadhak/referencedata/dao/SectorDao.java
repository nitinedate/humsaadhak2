package org.humsaadhak.referencedata.dao;

import java.util.List;
import java.util.Optional;
import org.humsaadhak.referencedata.dto.SectorRow;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Sectors with how many purposes, causes and projects use each (they store the sector name). */
@Repository
public class SectorDao {
    private static final String ROWS = """
        select s.code, s.name, s.active,
               (select count(*) from purpose p where p.sector = s.name) + (select count(*) from need n where n.sector = s.name) as used_by
        from sector s""";

    private final JdbcClient db;

    public SectorDao(JdbcClient db) { this.db = db; }

    public List<SectorRow> findAll() { return db.sql(ROWS + " order by s.name").query(SectorRow.class).list(); }

    public Optional<SectorRow> find(String code) { return db.sql(ROWS + " where s.code = ?").param(code).query(SectorRow.class).optional(); }

    public boolean existsCodeOrName(String code, String name) {
        return db.sql("select count(*) from sector where code = ? or lower(name) = lower(?)").params(code, name).query(Long.class).single() > 0;
    }

    public boolean nameTakenByAnother(String name, String code) {
        return db.sql("select count(*) from sector where lower(name) = lower(?) and code <> ?").params(name, code).query(Long.class).single() > 0;
    }

    public void insert(String code, String name) { db.sql("insert into sector(code, name, active) values (?, ?, true)").params(code, name).update(); }

    public void update(String code, String name, boolean active) {
        db.sql("update sector set name = ?, active = ? where code = ?").params(name, active, code).update();
    }
}
