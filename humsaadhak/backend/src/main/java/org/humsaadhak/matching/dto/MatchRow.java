package org.humsaadhak.matching.dto;

import org.springframework.web.bind.annotation.*;

public record MatchRow(long id, String refNo, long needId, String needName, String kind, java.math.BigDecimal remaining, int priority) {}
