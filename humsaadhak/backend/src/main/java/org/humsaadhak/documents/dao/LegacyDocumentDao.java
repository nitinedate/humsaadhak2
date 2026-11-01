package org.humsaadhak.documents.dao;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Documents migrated from the legacy system whose files still have to be copied into object storage. */
@Repository
public class LegacyDocumentDao {
    public record PendingFile(long id, String legacyPath, String storageKey, String contentType) {}

    private final JdbcClient db;

    public LegacyDocumentDao(JdbcClient db) { this.db = db; }

    public List<PendingFile> pendingFiles() {
        return db.sql("select id, legacy_path, storage_key, content_type from document where legacy_path is not null and size_bytes = 0")
                .query(PendingFile.class).list();
    }

    public void markCopied(long documentId, long sizeBytes) {
        db.sql("update document set size_bytes = ? where id = ?").params(sizeBytes, documentId).update();
    }
}
