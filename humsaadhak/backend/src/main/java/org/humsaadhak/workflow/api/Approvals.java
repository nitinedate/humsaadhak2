package org.humsaadhak.workflow.api;

/** What other modules may ask of the workflow engine. */
public interface Approvals {
    /** Creates a request in DRAFT for a new record. */
    long open(SubjectType type, long subjectId, long makerId, String title);
    /** Creates the request if needed and moves it to SUBMITTED. */
    long openAndSubmit(SubjectType type, long subjectId, long makerId, String title);
    void submit(SubjectType type, long subjectId, long makerId);
    /** A material edit to an approved record sends it back for approval (new revision). */
    void reopenAfterEdit(SubjectType type, long subjectId, long makerId);
    ApprovalStatus statusOf(SubjectType type, long subjectId);
}
