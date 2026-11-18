package com.sangyunpark99.tradeserver.account.repository;

import com.sangyunpark99.tradeserver.account.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
}
