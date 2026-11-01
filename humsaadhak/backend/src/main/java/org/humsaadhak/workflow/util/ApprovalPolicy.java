package org.humsaadhak.workflow.util;

import org.humsaadhak.shared.exception.DomainException;
import org.humsaadhak.workflow.api.ApprovalStatus;

/**
 * The maker-checker rules in one place (pure Java, unit-tested).
 *  - submit: from DRAFT or REJECTED, by the maker
 *  - approve / reject: only from SUBMITTED, never by the maker
 *  - reject: a comment is required
 *  - material edit of an APPROVED record: back to SUBMITTED as a new revision
 */
public final class ApprovalPolicy {
    private ApprovalPolicy() {}

    public static ApprovalStatus submit(ApprovalStatus current) {
        if (current != ApprovalStatus.DRAFT && current != ApprovalStatus.REJECTED)
            throw new DomainException("not-submittable", "Only drafts or rejected records can be submitted.");
        return ApprovalStatus.SUBMITTED;
    }

    public static ApprovalStatus decide(ApprovalStatus current, long makerId, long checkerId, boolean approve, String comment) {
        if (current != ApprovalStatus.SUBMITTED)
            throw new DomainException("not-submitted", "This record is not waiting for approval.");
        if (makerId == checkerId)
            throw new DomainException("maker-is-checker", "You cannot approve or reject your own submission.");
        if (!approve && (comment == null || comment.isBlank()))
            throw new DomainException("reason-required", "A reason is required to reject.");
        return approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED;
    }

    public static ApprovalStatus afterMaterialEdit(ApprovalStatus current) {
        return current == ApprovalStatus.APPROVED ? ApprovalStatus.SUBMITTED : current;
    }
}
