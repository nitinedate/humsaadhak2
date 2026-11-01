package org.humsaadhak.documents.repository;

import org.humsaadhak.documents.entity.DocumentType;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {
    boolean existsByCode(String code);
}
