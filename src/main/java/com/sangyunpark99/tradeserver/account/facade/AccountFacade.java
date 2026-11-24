package com.sangyunpark99.tradeserver.account.facade;

import com.sangyunpark99.tradeserver.account.controller.dto.DepositAccountResponse;
import com.sangyunpark99.tradeserver.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Retryable(includes = OptimisticLockingFailureException.class, maxRetries = 3, delay = 10)
public class AccountFacade {

    private final AccountService accountService;

    public DepositAccountResponse deposit(long userId, BigDecimal amount) {
        return accountService.deposit(userId, amount);
    }
}
