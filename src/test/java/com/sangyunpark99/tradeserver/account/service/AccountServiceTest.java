package com.sangyunpark99.tradeserver.account.service;

import com.sangyunpark99.tradeserver.account.Account;
import com.sangyunpark99.tradeserver.account.controller.dto.DepositAccountResponse;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    @DisplayName("이미 계좌가 있는 사용자가 다시 열면 ACCOUNT_ALREADY_EXISTS이고 계좌는 하나뿐이다")
    void rejectSecondAccount() {
        long userId = createUser("user@toss.im");
        accountService.open(userId);

        assertThatThrownBy(() -> accountService.open(userId))
                .isInstanceOfSatisfying(TradeException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_ALREADY_EXISTS));
        assertThat(accountRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 사용자가 동시에 계좌를 열어도 하나만 열리고 나머지는 ACCOUNT_ALREADY_EXISTS")
    void concurrentOpen() throws Exception {
        long userId = createUser("user@toss.im");
        int threadCount = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threadCount; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return accountService.open(userId);
                }));
            }
            ready.await();
            start.countDown();

            int opened = 0;
            int rejected = 0;
            for (Future<Long> result : results) {
                try {
                    result.get(10, TimeUnit.SECONDS);
                    opened++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause())
                            .isInstanceOfSatisfying(TradeException.class,
                                    t -> assertThat(t.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_ALREADY_EXISTS));
                    rejected++;
                }
            }

            assertThat(opened).isEqualTo(1);
            assertThat(rejected).isEqualTo(threadCount - 1);
            assertThat(accountRepository.count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("토큰의 사용자가 존재하지 않으면 UNAUTHORIZED이고 계좌는 만들어지지 않는다")
    void unknownUser() {
        assertThatThrownBy(() -> accountService.open(999_999L))
                .isInstanceOfSatisfying(TradeException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        assertThat(accountRepository.count()).isZero();
    }

    @Test
    @DisplayName("입금하면 계좌에 반영되고 입금 후 잔고와 주문 가능 금액을 돌려준다")
    void deposit() {
        long userId = createUser("user@toss.im");
        Long accountId = accountService.open(userId);

        DepositAccountResponse response = accountService.deposit(userId, new BigDecimal("1000000"));

        assertThat(response.balance()).isEqualByComparingTo("1000000");
        assertThat(response.availableBalance()).isEqualByComparingTo("1000000");
        assertThat(accountRepository.findById(accountId).orElseThrow().getBalance()).isEqualByComparingTo("1000000");
    }

    @Test
    @DisplayName("계좌가 없는 사용자가 입금하면 NOT_FOUND_ACCOUNT")
    void depositWithoutAccount() {
        long userId = createUser("user@toss.im");

        assertThatThrownBy(() -> accountService.deposit(userId, new BigDecimal("1000")))
                .isInstanceOfSatisfying(TradeException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND_ACCOUNT));
    }

    @Test
    @DisplayName("토큰의 사용자가 존재하지 않으면 입금도 UNAUTHORIZED")
    void depositUnknownUser() {
        assertThatThrownBy(() -> accountService.deposit(999_999L, new BigDecimal("1000")))
                .isInstanceOfSatisfying(TradeException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }
}
