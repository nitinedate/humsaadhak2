package org.humsaadhak.giving.repository;

import org.humsaadhak.giving.entity.Donation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DonationRepository extends JpaRepository<Donation, Long> {
    Optional<Donation> findByIdempotencyKey(String key);
    List<Donation> findByDonorOrgIdOrderByIdDesc(long donorOrgId);
    @Query("select coalesce(sum(d.paidAmount), 0) from Donation d where d.purposeId = ?1 and d.paidOn >= ?2")
    BigDecimal sumForPurposeSince(long purposeId, LocalDate since);
}
