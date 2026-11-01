package org.humsaadhak.workflow.dto;

import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

public record DecisionBody(@Size(max = 2000) String comment) {}
