package org.humsaadhak.documents.repository;

import org.humsaadhak.documents.entity.Document;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {
    List<Document> findByOwnerOrgIdAndSupersededFalseOrderByIdDesc(long ownerOrgId);
    Optional<Document> findFirstByOwnerOrgIdAndDocumentTypeIdAndSupersededFalse(long ownerOrgId, long typeId);
    List<Document> findByStateAndSupersededFalseOrderByIdAsc(Document.State state);
}
