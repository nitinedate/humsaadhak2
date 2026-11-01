package org.humsaadhak.giving.repository;

import org.humsaadhak.giving.entity.Instalment;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstalmentRepository extends JpaRepository<Instalment, Long> {
    List<Instalment> findByStatusAndDueDateBetween(Instalment.Status status, LocalDate from, LocalDate to);
    Optional<Instalment> findFirstByPlanIdAndStatusOrderByDueDateAsc(long planId, Instalment.Status status);
}
