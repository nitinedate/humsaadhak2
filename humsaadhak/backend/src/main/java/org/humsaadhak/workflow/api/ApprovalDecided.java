package org.humsaadhak.workflow.api;

public record ApprovalDecided(SubjectType subjectType, long subjectId, boolean approved, long checkerId, String comment) {}
