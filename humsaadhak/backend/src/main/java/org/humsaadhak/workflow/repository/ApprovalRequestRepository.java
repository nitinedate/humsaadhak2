package org.humsaadhak.workflow.repository;

import org.humsaadhak.workflow.entity.ApprovalRequest;

import java.util.Optional;
import org.humsaadhak.workflow.api.ApprovalStatus;
import org.humsaadhak.workflow.api.SubjectType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {
    Optional<ApprovalRequest> findBySubjectTypeAndSubjectId(SubjectType type, long subjectId);
    Page<ApprovalRequest> findByStatus(ApprovalStatus status, Pageable pageable);
    Page<ApprovalRequest> findByStatusAndSubjectType(ApprovalStatus status, SubjectType type, Pageable pageable);
}
