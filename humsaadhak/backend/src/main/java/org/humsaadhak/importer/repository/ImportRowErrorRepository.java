package org.humsaadhak.importer.repository;

import org.humsaadhak.importer.entity.ImportRowError;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRowErrorRepository extends JpaRepository<ImportRowError, Long> {
    List<ImportRowError> findByJobIdOrderByRowNumberAscIdAsc(long jobId);
}
