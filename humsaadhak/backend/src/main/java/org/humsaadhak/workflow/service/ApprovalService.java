package org.humsaadhak.workflow.service;

import org.humsaadhak.shared.model.AuditTarget;

import org.humsaadhak.shared.security.AuditContext;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.humsaadhak.workflow.dto.StepRow;
import org.humsaadhak.workflow.dto.ApprovalRow;
import org.humsaadhak.shared.dto.PageResponse;
import java.util.List;
import org.humsaadhak.workflow.util.ApprovalPolicy;
import org.humsaadhak.workflow.entity.ApprovalRequest;
import org.humsaadhak.workflow.repository.ApprovalRequestRepository;
import org.humsaadhak.workflow.entity.ApprovalStep;
import org.humsaadhak.workflow.repository.ApprovalStepRepository;

import org.humsaadhak.shared.exception.NotFoundException;
import org.humsaadhak.workflow.api.ApprovalDecided;
import org.humsaadhak.workflow.api.ApprovalStatus;
import org.humsaadhak.workflow.api.Approvals;
import org.humsaadhak.workflow.api.SubjectType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ApprovalService implements Approvals {
    private final ApprovalRequestRepository requests;
    private final ApprovalStepRepository steps;
    private final ApplicationEventPublisher events;

    public ApprovalService(ApprovalRequestRepository requests, ApprovalStepRepository steps, ApplicationEventPublisher events) {
        this.requests = requests; this.steps = steps; this.events = events;
    }

    @Override
    public long open(SubjectType type, long subjectId, long makerId, String title) {
        return requests.findBySubjectTypeAndSubjectId(type, subjectId)
                .orElseGet(() -> {
                    ApprovalRequest r = requests.save(new ApprovalRequest(type, subjectId, makerId, title));
                    steps.save(new ApprovalStep(r.getId(), null, ApprovalStatus.DRAFT, makerId, null));
                    return r;
                }).getId();
    }

    @Override
    public long openAndSubmit(SubjectType type, long subjectId, long makerId, String title) {
        open(type, subjectId, makerId, title);
        submit(type, subjectId, makerId);
        return find(type, subjectId).getId();
    }

    @Override
    public void submit(SubjectType type, long subjectId, long makerId) {
        ApprovalRequest r = find(type, subjectId);
        ApprovalStatus next = ApprovalPolicy.submit(r.getStatus());
        transition(r, next, makerId, null);
    }

    @Override
    public void reopenAfterEdit(SubjectType type, long subjectId, long makerId) {
        ApprovalRequest r = find(type, subjectId);
        ApprovalStatus next = ApprovalPolicy.afterMaterialEdit(r.getStatus());
        if (next != r.getStatus()) {
            r.newRevision(makerId);
            transition(r, next, makerId, "Edited after approval");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalStatus statusOf(SubjectType type, long subjectId) {
        return requests.findBySubjectTypeAndSubjectId(type, subjectId).map(ApprovalRequest::getStatus).orElse(ApprovalStatus.DRAFT);
    }

    public ApprovalRequest decide(long requestId, long checkerId, boolean approve, String comment) {
        ApprovalRequest r = requests.findById(requestId).orElseThrow(() -> new NotFoundException("Approval request", requestId));
        AuditContext.target(AuditTarget.valueOf(r.getSubjectType().name()), r.getSubjectId());   // the trail of the record decided on
        AuditContext.note(r.getSubjectTitle());
        ApprovalStatus next = ApprovalPolicy.decide(r.getStatus(), r.getMakerId(), checkerId, approve, comment);
        transition(r, next, checkerId, comment);
        events.publishEvent(new ApprovalDecided(r.getSubjectType(), r.getSubjectId(), approve, checkerId, comment));
        return r;
    }

    private void transition(ApprovalRequest r, ApprovalStatus next, long actor, String comment) {
        steps.save(new ApprovalStep(r.getId(), r.getStatus(), next, actor, comment));
        r.moveTo(next, comment);
    }

    private ApprovalRequest find(SubjectType type, long subjectId) {
        return requests.findBySubjectTypeAndSubjectId(type, subjectId)
                .orElseThrow(() -> new NotFoundException("Approval request for " + type, subjectId));
    }

    /** Approval requests in a status (optionally of one kind), oldest first: whoever waited longest is decided first. */
    @Transactional(readOnly = true)
    public PageResponse<ApprovalRow> list(ApprovalStatus status, SubjectType type, int page, int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("updatedAt").ascending());
        var result = type == null ? requests.findByStatus(status, pageable) : requests.findByStatusAndSubjectType(status, type, pageable);
        return PageResponse.of(result, r -> new ApprovalRow(r.getId(), r.getSubjectType(), r.getSubjectId(), r.getSubjectTitle(), r.getMakerId(),
                r.getStatus(), r.getRevision(), r.getLastComment(), r.getUpdatedAt()));
    }

    /** Every step of one request, in order. */
    @Transactional(readOnly = true)
    public List<StepRow> history(long requestId) {
        return steps.findByRequestIdOrderByIdAsc(requestId).stream()
                .map(s -> new StepRow(s.getFromStatus(), s.getToStatus(), s.getActorId(), s.getComment(), s.getCreatedAt())).toList();
    }
}
