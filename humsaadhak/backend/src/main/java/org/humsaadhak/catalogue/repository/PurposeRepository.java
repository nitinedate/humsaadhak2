package org.humsaadhak.catalogue.repository;

import org.humsaadhak.catalogue.entity.Purpose;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurposeRepository extends JpaRepository<Purpose, Long> {
    List<Purpose> findByDonorOrgIdOrderByIdDesc(long donorOrgId);
}
