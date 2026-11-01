package org.humsaadhak.matching.repository;

import org.humsaadhak.matching.entity.MatchCandidate;

import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchCandidateRepository extends JpaRepository<MatchCandidate, Long> {
    List<MatchCandidate> findByPurposeIdAndFinalScoreGreaterThanEqualAndDecisionOrderByEngineRankAsc(
            long purposeId, int minScore, MatchCandidate.Decision decision);

    /** Serialize reviews for one purpose so two reviewers cannot confirm the same pending rows concurrently. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from MatchCandidate c where c.purposeId = :purposeId order by c.engineRank asc")
    List<MatchCandidate> lockPurposeCandidates(@Param("purposeId") long purposeId);

    boolean existsByPurposeIdAndNeedId(long purposeId, long needId);
    List<MatchCandidate> findByPurposeId(long purposeId);
}
