package org.humsaadhak.workflow.api;

/** Replaces the legacy integer codes: 1 draft, 5 submitted, 2 approved, 3 rejected. */
public enum ApprovalStatus {
    DRAFT(1), SUBMITTED(5), APPROVED(2), REJECTED(3);

    private final int legacyCode;
    ApprovalStatus(int legacyCode) { this.legacyCode = legacyCode; }
    public int legacyCode() { return legacyCode; }

    public static ApprovalStatus fromLegacyCode(int code) {
        for (ApprovalStatus s : values()) if (s.legacyCode == code) return s;
        throw new IllegalArgumentException("Unknown status code " + code);
    }
}
