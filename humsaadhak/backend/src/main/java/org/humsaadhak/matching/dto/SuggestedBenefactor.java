package org.humsaadhak.matching.dto;

import org.springframework.web.bind.annotation.*;

/** Reverse search: purposes (and so benefactors) that fit a need. Shown to its owner and to staff. */
public record SuggestedBenefactor(long purposeId, String purposeName, long donorOrgId, int score) {}
