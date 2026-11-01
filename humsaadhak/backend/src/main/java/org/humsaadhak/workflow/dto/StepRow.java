package org.humsaadhak.workflow.dto;

import java.time.Instant;
import org.humsaadhak.workflow.api.ApprovalStatus;
import org.springframework.web.bind.annotation.*;

public record StepRow(ApprovalStatus from, ApprovalStatus to, long actorId, String comment, Instant at) {}
