package com.sangyunpark99.tradeserver.account.facade;

import com.sangyunpark99.tradeserver.account.Account;
import com.sangyunpark99.tradeserver.account.repository.AccountRepository;
import com.sangyunpark99.tradeserver.account.service.AccountService;
import com.sangyunpark99.tradeserver.fill.repository.FillRepository;
import com.sangyunpark99.tradeserver.order.facade.OrderFacade;
import com.sangyunpark99.tradeserver.order.repository.OrderRepository;
import com.sangyunpark99.tradeserver.user.User;
import com.sangyunpark99.tradeserver.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DepositFillConcurrencyTest {

    private static final int ROUND_COUNT = 50;
    private static final BigDecimal INITIAL_DEPOSIT = new BigDecimal("10000000");
    private static final BigDecimal PRICE = new BigDecimal("1000000");
    private static final long FILL_QUANTITY = 3;
    private static final BigDecimal DEPOSIT = new BigDecimal("500000");
    private static final BigDecimal EXPECTED_BALANCE = new BigDecimal("7500000");

    @Autowired
    private AccountFacade accountFacade;

    @Autowired
    private AccountService accountService;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private FillRepository fillRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        fillRepository.deleteAll();
        orderRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("입금과 체결 반영이 같은 계좌에 동시에 들어와도 둘 다 반영된다")
    void depositAndFillOnSameAccount() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        int mismatched = 0;
        int failed = 0;
        try {
            for (int round = 0; round < ROUND_COUNT; round++) {
                long userId = userRepository.save(new User("user" + round + "@toss.im", "박상윤", "$2a$10$hash")).getId();
                Long accountId = accountService.open(userId);
                accountFacade.deposit(userId, INITIAL_DEPOSIT);
                Long orderId = orderFacade.placeOrder(userId, accountId, "AAPL", FILL_QUANTITY, PRICE);

                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                Future<?> deposit = pool.submit(() -> run(ready, start, () -> accountFacade.deposit(userId, DEPOSIT)));
                Future<?> fill = pool.submit(() -> run(ready, start,
                        () -> orderFacade.applyFill(UUID.randomUUID().toString(), orderId, FILL_QUANTITY, PRICE)));
                ready.await();
                start.countDown();

                failed += waitFor(deposit) + waitFor(fill);

                Account account = accountRepository.findById(accountId).orElseThrow();
                if (account.getBalance().compareTo(EXPECTED_BALANCE) != 0
                        || account.getReservedBalance().compareTo(BigDecimal.ZERO) != 0) {
                    mismatched++;
                }
            }
        } finally {
            pool.shutdownNow();
        }

        System.out.printf("입금·체결 동시 %d회 중 잔고 불일치 %d회, 실패 %d건%n", ROUND_COUNT, mismatched, failed);
        assertThat(failed).isZero();
        assertThat(mismatched).isZero();
    }

    private void run(CountDownLatch ready, CountDownLatch start, Runnable task) {
        ready.countDown();
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        task.run();
    }

    private int waitFor(Future<?> future) throws Exception {
        try {
            future.get(10, TimeUnit.SECONDS);
            return 0;
        } catch (ExecutionException e) {
            System.out.println("처리 실패: " + e.getCause());
            return 1;
        }
    }
}
