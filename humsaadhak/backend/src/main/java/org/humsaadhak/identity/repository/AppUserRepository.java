package org.humsaadhak.identity.repository;

import org.humsaadhak.identity.entity.AppUser;
import org.humsaadhak.identity.model.UserStatus;

import java.util.List;
import java.util.Optional;
import org.humsaadhak.shared.security.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<AppUser> findByOrgId(Long orgId);
    boolean existsByRole(Role role);
    Page<AppUser> findByStatus(UserStatus status, Pageable pageable);
    Page<AppUser> findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(String email, String name, Pageable pageable);
}
