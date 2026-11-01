package org.humsaadhak.documents.repository;

import org.humsaadhak.documents.entity.ShareRequest;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareRequestRepository extends JpaRepository<ShareRequest, Long> {
    Optional<ShareRequest> findByNeedIdAndDonorOrgId(long needId, long donorOrgId);
    List<ShareRequest> findByOwnerOrgIdOrderByIdDesc(long ownerOrgId);
    List<ShareRequest> findByDonorOrgIdOrderByIdDesc(long donorOrgId);
    boolean existsByOwnerOrgIdAndDonorOrgIdAndStatus(long ownerOrgId, long donorOrgId, ShareRequest.Status status);
}
