package org.humsaadhak.matching.api;

import java.util.List;

public record MatchesConfirmed(long batchId, String batchRefNo, long purposeId, long donorOrgId, List<Long> matchIdsInOrder) {}
