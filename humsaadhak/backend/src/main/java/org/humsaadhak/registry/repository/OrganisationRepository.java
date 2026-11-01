package org.humsaadhak.registry.repository;

import org.humsaadhak.registry.entity.Organisation;

import java.util.Optional;
import org.humsaadhak.registry.api.OrganisationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganisationRepository extends JpaRepository<Organisation, Long> {
    Optional<Organisation> findFirstByNameIgnoreCaseAndTypeAndVerifiedTrue(String name, OrganisationType type);
    Optional<Organisation> findByRefNo(String refNo);
    Page<Organisation> findByType(OrganisationType type, Pageable pageable);
    Page<Organisation> findByTypeAndVerified(OrganisationType type, boolean verified, Pageable pageable);
    Page<Organisation> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
