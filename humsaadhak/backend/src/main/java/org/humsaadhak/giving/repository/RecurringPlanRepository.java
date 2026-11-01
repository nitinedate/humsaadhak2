package org.humsaadhak.giving.repository;

import org.humsaadhak.giving.entity.RecurringPlan;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringPlanRepository extends JpaRepository<RecurringPlan, Long> {
    Optional<RecurringPlan> findByDonorOrgIdAndNeedId(long donorOrgId, long needId);
}
