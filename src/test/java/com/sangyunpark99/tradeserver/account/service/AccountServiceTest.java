package com.sangyunpark99.tradeserver.account.service;

import com.sangyunpark99.tradeserver.account.Account;
import com.sangyunpark99.tradeserver.account.repository.AccountRepository;
import com.sangyunpark99.tradeserver.common.exception.ErrorCode;
import com.sangyunpark99.tradeserver.common.exception.TradeException;
import com.sangyunpark99.tradeserver.user.User;
import com.sangyunpark99.tradeserver.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AccountServiceTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    private long createUser(String email) {
        return userRepository.save(new User(email, "박상윤", "$2a$10$hash")).getId();
    }

    @Test
    @DisplayName("계좌를 열면 요청한 사용자의 0원 계좌가 만들어진다")
    void openWithZeroBalance() {
        long userId = createUser("user@toss.im");

        Long accountId = accountService.open(userId);

        Account account = accountRepository.findById(accountId).orElseThrow();
        assertThat(account.isOwnedBy(userId)).isTrue();
        assertThat(account.getBalance()).isEqualByComparingTo("0");
        assertThat(account.getReservedBalance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("한 사용자가 계좌를 여러 개 열 수 있다")
    void openMultipleAccounts() {
        long userId = createUser("user@toss.im");

        Long first = accountService.open(userId);
        Long second = accountService.open(userId);

        assertThat(first).isNotEqualTo(second);
        assertThat(accountRepository.findById(first).orElseThrow().isOwnedBy(userId)).isTrue();
        assertThat(accountRepository.findById(second).orElseThrow().isOwnedBy(userId)).isTrue();
    }

    @Test
    @DisplayName("토큰의 사용자가 존재하지 않으면 UNAUTHORIZED이고 계좌는 만들어지지 않는다")
    void unknownUser() {
        assertThatThrownBy(() -> accountService.open(999_999L))
                .isInstanceOfSatisfying(TradeException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        assertThat(accountRepository.count()).isZero();
    }
}
