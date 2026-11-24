package com.sangyunpark99.tradeserver.account.service;

import com.sangyunpark99.tradeserver.account.Account;
import com.sangyunpark99.tradeserver.account.controller.dto.DepositAccountResponse;
import com.sangyunpark99.tradeserver.account.repository.AccountRepository;
import com.sangyunpark99.tradeserver.common.exception.ErrorCode;
import com.sangyunpark99.tradeserver.common.exception.TradeException;
import com.sangyunpark99.tradeserver.user.User;
import com.sangyunpark99.tradeserver.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    @Transactional
    public Long open(long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new TradeException(ErrorCode.UNAUTHORIZED));
        Account account = new Account(user.getId(), BigDecimal.ZERO);

        return accountRepository.save(account).getId();
    }

    @Transactional
    public DepositAccountResponse deposit(long userId, BigDecimal amount) {
        userRepository.findById(userId).orElseThrow(() -> new TradeException(ErrorCode.UNAUTHORIZED));
        Account account = accountRepository.findByUserId(userId).orElseThrow(() -> new TradeException(ErrorCode.NOT_FOUND_ACCOUNT));
        account.deposit(amount);

        return new DepositAccountResponse(account.getBalance(), account.getAvailableBalance());
    }
}