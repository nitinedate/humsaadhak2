package org.humsaadhak.matching.repository;

import org.humsaadhak.matching.entity.Match;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findByPurposeIdOrderByPriorityAsc(long purposeId);
    boolean existsByDonorOrgIdAndNeedId(long donorOrgId, long needId);
}
