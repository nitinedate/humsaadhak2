package org.humsaadhak.registry.repository;

import org.humsaadhak.registry.entity.OrgContact;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgContactRepository extends JpaRepository<OrgContact, Long> {
    List<OrgContact> findByOrgIdOrderByIdAsc(long orgId);
    void deleteByOrgId(long orgId);
}
