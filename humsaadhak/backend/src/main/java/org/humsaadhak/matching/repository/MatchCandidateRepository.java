package org.humsaadhak.matching.repository;

import org.humsaadhak.matching.entity.MatchCandidate;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchCandidateRepository extends JpaRepository<MatchCandidate, Long> {
    List<MatchCandidate> findByPurposeIdAndFinalScoreGreaterThanEqualOrderByEngineRankAsc(long purposeId, int minScore);
    boolean existsByPurposeIdAndNeedId(long purposeId, long needId);
    List<MatchCandidate> findByPurposeId(long purposeId);
}
