package com.finance_assistant.account;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
    boolean existsByNameIgnoreCase(String name);

    List<Account> findAllByOrderByIdAsc();
}
