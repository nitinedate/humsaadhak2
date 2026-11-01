package org.humsaadhak.workflow.controller;

import org.humsaadhak.shared.security.AuditAction;

import java.util.List;
import org.humsaadhak.shared.dto.PageResponse;
import org.humsaadhak.shared.security.CurrentUser;
import org.humsaadhak.workflow.api.ApprovalStatus;
import org.humsaadhak.workflow.api.SubjectType;
import org.humsaadhak.workflow.dto.ApprovalRow;
import org.humsaadhak.workflow.dto.DecisionBody;
import org.humsaadhak.workflow.dto.StepRow;
import org.humsaadhak.workflow.service.ApprovalService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** The approvals queue (maker ≠ checker is enforced by ApprovalService). */
@RestController
@RequestMapping("/api/v2/approvals")
@PreAuthorize("hasAnyAuthority('perm:record.approve','perm:user.approve','perm:org.approve')")
public class ApprovalController {
    private final ApprovalService approvals;

    public ApprovalController(ApprovalService approvals) { this.approvals = approvals; }

    /** Oldest first: whoever waited longest is decided first. */
    @GetMapping
    public PageResponse<ApprovalRow> list(@RequestParam(defaultValue = "SUBMITTED") ApprovalStatus status, @RequestParam(required = false) SubjectType type,
                                          @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return approvals.list(status, type, page, size);
    }

    @GetMapping("/{id}/history")
    public List<StepRow> history(@PathVariable long id) { return approvals.history(id); }

    @AuditAction(value = "approval.approve", detail = "#body == null ? null : #body.comment()")
    @PostMapping("/{id}/approve")
    public void approve(@PathVariable long id, @RequestBody(required = false) DecisionBody body) {
        approvals.decide(id, CurrentUser.get().id(), true, body == null ? null : body.comment());
    }

    @AuditAction(value = "approval.reject", detail = "#body.comment()")
    @PostMapping("/{id}/reject")
    public void reject(@PathVariable long id, @RequestBody DecisionBody body) { approvals.decide(id, CurrentUser.get().id(), false, body.comment()); }
}
