package org.humsaadhak.catalogue.repository;

import org.humsaadhak.catalogue.entity.Need;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NeedRepository extends JpaRepository<Need, Long> {
    List<Need> findByOrgIdOrderByIdDesc(long orgId);
    List<Need> findByApprovedTrueAndCompletedFalseOrderByIdDesc();
    List<Need> findByApprovedTrueAndCompletedFalseAndSectorOrderByIdDesc(String sector);
}
