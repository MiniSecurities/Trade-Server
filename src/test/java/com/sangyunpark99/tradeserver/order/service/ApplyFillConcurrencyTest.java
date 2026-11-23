package com.sangyunpark99.tradeserver.order.service;

import com.sangyunpark99.tradeserver.account.Account;
import com.sangyunpark99.tradeserver.account.repository.AccountRepository;
import com.sangyunpark99.tradeserver.order.facade.OrderFacade;
import com.sangyunpark99.tradeserver.order.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApplyFillConcurrencyTest {

    private static final int ROUND_COUNT = 100;
    private static final BigDecimal INITIAL_BALANCE = new BigDecimal("10000000");
    private static final BigDecimal PRICE = new BigDecimal("1000000");
    private static final long KAKAO_QUANTITY = 3;
    private static final long SAMSUNG_QUANTITY = 5;
    private static final BigDecimal EXPECTED_BALANCE = new BigDecimal("2000000");

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OrderRepository orderRepository;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 계좌의 두 주문 체결을 동시에 반영해도 잔고는 두 체결 금액만큼 빠진다")
    void concurrentFillsOnSameAccount() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        int lostUpdate = 0;
        int failed = 0;
        long startedAt = System.nanoTime();
        try {
            for (int round = 0; round < ROUND_COUNT; round++) {
                Long accountId = accountRepository.save(new Account(INITIAL_BALANCE)).getId();
                Long kakaoOrderId = orderFacade.placeOrder(accountId, "KAKAO", KAKAO_QUANTITY, PRICE);
                Long samsungOrderId = orderFacade.placeOrder(accountId, "SAMSUNG", SAMSUNG_QUANTITY, PRICE);

                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                Future<?> kakao = pool.submit(() -> fill(kakaoOrderId, KAKAO_QUANTITY, ready, start));
                Future<?> samsung = pool.submit(() -> fill(samsungOrderId, SAMSUNG_QUANTITY, ready, start));
                ready.await();
                start.countDown();

                int roundFailed = waitFor(kakao) + waitFor(samsung);
                failed += roundFailed;

                Account account = accountRepository.findById(accountId).orElseThrow();
                boolean mismatched = account.getBalance().compareTo(EXPECTED_BALANCE) != 0
                        || account.getReservedBalance().compareTo(BigDecimal.ZERO) != 0;
                if (roundFailed == 0 && mismatched) {
                    lostUpdate++;
                }
            }
        } finally {
            pool.shutdownNow();
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        System.out.printf("동시 체결 %d회 중 갱신 손실 %d회, 체결 반영 실패 %d건, 걸린 시간 %dms%n",
                ROUND_COUNT, lostUpdate, failed, elapsedMs);
        assertThat(failed).isZero();
        assertThat(lostUpdate).isZero();
    }

    private void fill(Long orderId, long quantity, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        orderFacade.applyFill(orderId, quantity, PRICE);
    }

    private int waitFor(Future<?> future) throws Exception {
        try {
            future.get(5, TimeUnit.SECONDS);
            return 0;
        } catch (ExecutionException e) {
            System.out.println("체결 반영 실패: " + e.getCause());
            return 1;
        }
    }
}
