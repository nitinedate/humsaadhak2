package org.humsaadhak.documents.repository;

import org.humsaadhak.documents.entity.DocumentTypeRule;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DocumentTypeRuleRepository extends JpaRepository<DocumentTypeRule, Long> {
    List<DocumentTypeRule> findByDocumentTypeId(long documentTypeId);
    @Query("select r from DocumentTypeRule r where r.organisationType = ?1 and r.mandatory = true")
    List<DocumentTypeRule> findMandatory(String organisationType);
}
