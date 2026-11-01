package org.humsaadhak.matching.api;

import java.util.Optional;

public interface Matches {
    record MatchView(long id, String refNo, long purposeId, long needId, long donorOrgId, int priority) {}
    boolean donorHasConfirmedMatch(long donorOrgId, long needId);
    Optional<MatchView> match(long matchId);
}
