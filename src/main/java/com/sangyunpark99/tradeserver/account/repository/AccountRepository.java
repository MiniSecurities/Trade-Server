package com.sangyunpark99.tradeserver.account.repository;

import com.sangyunpark99.tradeserver.account.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUserId(long userId);

    boolean existsAccountByUserId(long userId);
}
