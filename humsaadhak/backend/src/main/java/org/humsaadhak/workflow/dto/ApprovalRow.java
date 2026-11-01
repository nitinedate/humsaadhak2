package org.humsaadhak.workflow.dto;

import java.time.Instant;
import org.humsaadhak.workflow.api.ApprovalStatus;
import org.humsaadhak.workflow.api.SubjectType;
import org.springframework.web.bind.annotation.*;

public record ApprovalRow(long id, SubjectType subjectType, long subjectId, String title, long makerId, ApprovalStatus status,
                   int revision, String lastComment, Instant submittedAt) {}
