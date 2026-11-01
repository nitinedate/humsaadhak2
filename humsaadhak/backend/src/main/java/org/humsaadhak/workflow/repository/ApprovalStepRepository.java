package org.humsaadhak.workflow.repository;

import org.humsaadhak.workflow.entity.ApprovalStep;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, Long> {
    List<ApprovalStep> findByRequestIdOrderByIdAsc(long requestId);
}
