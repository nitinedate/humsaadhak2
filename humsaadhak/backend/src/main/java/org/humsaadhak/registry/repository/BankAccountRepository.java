package org.humsaadhak.registry.repository;

import org.humsaadhak.registry.entity.BankAccount;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {
    List<BankAccount> findByOrgIdOrderByIdAsc(long orgId);
}
