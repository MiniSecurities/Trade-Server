package com.sangyunpark99.tradeserver.order.service;

import com.sangyunpark99.tradeserver.account.Account;
import com.sangyunpark99.tradeserver.account.repository.AccountRepository;
import com.sangyunpark99.tradeserver.order.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StartSendingConcurrencyTest {

    private static final int ORDER_COUNT = 100;

    @Autowired
    private OrderService orderService;

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
    @DisplayName("두 스레드가 같은 주문을 동시에 선점해도 한쪽만 성공한다")
    void onlyOneThreadClaims() throws Exception {
        Long accountId = accountRepository.save(new Account(new BigDecimal("100000000"))).getId();
        List<Long> orderIds = new ArrayList<>();
        for (int i = 0; i < ORDER_COUNT; i++) {
            orderIds.add(orderService.placeOrder(accountId, "AAPL", 1, new BigDecimal("100")));
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);
        int duplicated = 0;
        try {
            for (Long orderId : orderIds) {
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                Future<Boolean> worker = pool.submit(() -> claim(orderId, ready, start));
                Future<Boolean> scheduler = pool.submit(() -> claim(orderId, ready, start));
                ready.await();
                start.countDown();

                int claimed = (worker.get(5, TimeUnit.SECONDS) ? 1 : 0) + (scheduler.get(5, TimeUnit.SECONDS) ? 1 : 0);
                assertThat(claimed).as("orderId=%d 선점 성공 수", orderId).isGreaterThanOrEqualTo(1);
                if (claimed == 2) {
                    duplicated++;
                }
            }
        } finally {
            pool.shutdownNow();
        }

        System.out.printf("동시 선점 %d건 중 둘 다 성공(중복 전송 위험) %d건%n", ORDER_COUNT, duplicated);
        assertThat(duplicated).isZero();
    }

    private boolean claim(Long orderId, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return orderService.startSending(orderId);
    }
}
