package com.sangyunpark99.tradeserver.order.service;

import java.util.UUID;
import com.sangyunpark99.tradeserver.fill.repository.FillRepository;
import com.sangyunpark99.tradeserver.account.Account;
import com.sangyunpark99.tradeserver.account.repository.AccountRepository;
import com.sangyunpark99.tradeserver.order.entity.Order;
import com.sangyunpark99.tradeserver.order.entity.OrderStatus;
import com.sangyunpark99.tradeserver.order.facade.OrderFacade;
import com.sangyunpark99.tradeserver.order.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OrderLockTest {

    private static final BigDecimal PRICE = new BigDecimal("100000");
    private static final long ORDER_QUANTITY = 10;
    private static final long FILL_QUANTITY = 3;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private FillRepository fillRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void tearDown() {
        fillRepository.deleteAll();
        orderRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("접수 확인이 주문을 잠그고 있는 동안 체결 반영은 기다렸다가 반영되어 체결 수량이 사라지지 않는다")
    void fillWaitsWhileAcceptHoldsOrderLock() throws Exception {
        Long accountId = accountRepository.save(new Account(new BigDecimal("10000000"))).getId();
        Long orderId = orderFacade.placeOrder(accountId, "KAKAO", ORDER_QUANTITY, PRICE);
        orderService.startSending(orderId);

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> slowAccept = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                Order order = orderRepository.findWithLockById(orderId).orElseThrow();
                locked.countDown();
                await(release);
                order.accept();
            }));
            locked.await();

            Future<?> fill = pool.submit(() -> orderFacade.applyFill(UUID.randomUUID().toString(), orderId, FILL_QUANTITY, PRICE));

            assertThatThrownBy(() -> fill.get(500, TimeUnit.MILLISECONDS))
                    .as("접수 확인이 잠금을 쥐고 있는 동안 체결 반영은 끝나면 안 된다")
                    .isInstanceOf(TimeoutException.class);

            release.countDown();
            slowAccept.get(5, TimeUnit.SECONDS);
            fill.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getFilledQuantity()).isEqualTo(FILL_QUANTITY);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
