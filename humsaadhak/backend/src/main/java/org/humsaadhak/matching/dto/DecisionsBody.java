package org.humsaadhak.matching.dto;

import org.humsaadhak.matching.util.CandidateDecisionValidator;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.web.bind.annotation.*;

public record DecisionsBody(@NotEmpty List<CandidateDecisionValidator.Decision> decisions) {}
