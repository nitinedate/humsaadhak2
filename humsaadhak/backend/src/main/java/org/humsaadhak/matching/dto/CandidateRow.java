package org.humsaadhak.matching.dto;

import org.springframework.web.bind.annotation.*;

public record CandidateRow(long id, long needId, String needName, String kind, String sector, java.math.BigDecimal remaining,
                    int score, int engineRank, String explanation, String decision, String remark, Integer priority,
                    String decidedBy, java.time.Instant decidedAt) {}
