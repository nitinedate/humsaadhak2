package org.humsaadhak.importer.repository;

import org.humsaadhak.importer.entity.ImportJob;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {
    Page<ImportJob> findAllByOrderByIdDesc(Pageable pageable);
}
