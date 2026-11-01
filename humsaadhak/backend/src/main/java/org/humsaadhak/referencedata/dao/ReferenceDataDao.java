package org.humsaadhak.referencedata.dao;

import java.util.List;
import org.humsaadhak.referencedata.dto.DocumentTypeOption;
import org.humsaadhak.referencedata.dto.Option;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Active reference data for forms. */
@Repository
public class ReferenceDataDao {
    private final JdbcClient db;

    public ReferenceDataDao(JdbcClient db) { this.db = db; }

    public List<Option> activeSectors() { return db.sql("select code, name from sector where active order by name").query(Option.class).list(); }

    public List<Option> states() { return db.sql("select code, name from state_region order by name").query(Option.class).list(); }

    /** With the organisation types each one is compulsory for, so upload forms can highlight them. */
    public List<DocumentTypeOption> activeDocumentTypes() {
        return db.sql("""
                select t.id, t.code, t.name, t.category, t.shareable,
                       coalesce((select string_agg(r.organisation_type, ',' order by r.organisation_type)
                                 from document_type_rule r where r.document_type_id = t.id and r.mandatory), '') as mandatory_for
                from document_type t where t.active order by t.name""")
                .query((rs, n) -> new DocumentTypeOption(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                        rs.getString("category"), rs.getBoolean("shareable"), commaList(rs.getString("mandatory_for"))))
                .list();
    }

    static List<String> commaList(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split(","));
    }
}
