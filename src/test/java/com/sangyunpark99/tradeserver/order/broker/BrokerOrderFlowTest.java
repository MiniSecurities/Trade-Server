package com.sangyunpark99.tradeserver.order.broker;

import com.sangyunpark99.tradeserver.account.Account;
import com.sangyunpark99.tradeserver.account.repository.AccountRepository;
import com.sangyunpark99.tradeserver.common.exception.TradeException;
import com.sangyunpark99.tradeserver.order.entity.OrderStatus;
import com.sangyunpark99.tradeserver.order.repository.OrderRepository;
import com.sangyunpark99.tradeserver.order.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "broker.order-sending.enabled=true")
class BrokerOrderFlowTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OrderRepository orderRepository;

    @MockitoBean
    private BrokerClient brokerClient;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        accountRepository.deleteAll();
    }

    private Long createAccount(String balance) {
        return accountRepository.save(new Account(new BigDecimal(balance))).getId();
    }

    private void awaitStatus(Long orderId, OrderStatus expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (orderRepository.findById(orderId).orElseThrow().getOrderStatus() == expected) {
                return;
            }
            Thread.sleep(50);
        }
        assertThat(orderRepository.findById(orderId).orElseThrow().getOrderStatus()).isEqualTo(expected);
    }

    @Test
    @DisplayName("주문이 커밋되면 브로커에 주문 내용을 보낸다")
    void sendsOrderAfterCommit() {
        given(brokerClient.sendOrder(any())).willReturn(BrokerResult.ACCEPTED);
        Long accountId = createAccount("10000");

        Long orderId = orderService.placeOrder(accountId, "AAPL", 10, new BigDecimal("200"));

        verify(brokerClient, timeout(5_000)).sendOrder(argThat(r ->
                r.orderId().equals(orderId)
                        && r.symbol().equals("AAPL")
                        && r.quantity() == 10L
                        && r.limitPrice().compareTo(new BigDecimal("200")) == 0));
    }

    @Test
    @DisplayName("브로커가 받으면 ACCEPTED, 묶인 돈은 그대로")
    void accepted() throws InterruptedException {
        given(brokerClient.sendOrder(any())).willReturn(BrokerResult.ACCEPTED);
        Long accountId = createAccount("10000");

        Long orderId = orderService.placeOrder(accountId, "AAPL", 10, new BigDecimal("200"));

        awaitStatus(orderId, OrderStatus.ACCEPTED);
        assertThat(accountRepository.findById(accountId).orElseThrow().getReservedBalance()).isEqualByComparingTo("2000");
    }

    @Test
    @DisplayName("결과를 모르면 UNKNOWN, 묶인 돈은 그대로")
    void unknown() throws InterruptedException {
        given(brokerClient.sendOrder(any())).willReturn(BrokerResult.UNKNOWN);
        Long accountId = createAccount("10000");

        Long orderId = orderService.placeOrder(accountId, "AAPL", 10, new BigDecimal("200"));

        awaitStatus(orderId, OrderStatus.UNKNOWN);
        assertThat(accountRepository.findById(accountId).orElseThrow().getReservedBalance()).isEqualByComparingTo("2000");
    }

    @Test
    @DisplayName("브로커가 거절하면 REJECTED, 묶인 돈이 풀린다")
    void rejected() throws InterruptedException {
        given(brokerClient.sendOrder(any())).willReturn(BrokerResult.REJECTED);
        Long accountId = createAccount("10000");

        Long orderId = orderService.placeOrder(accountId, "AAPL", 10, new BigDecimal("200"));

        awaitStatus(orderId, OrderStatus.REJECTED);
        assertThat(accountRepository.findById(accountId).orElseThrow().getReservedBalance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("주문이 롤백되면 브로커를 호출하지 않는다")
    void noCallWhenRolledBack() {
        Long accountId = createAccount("1999");

        assertThatThrownBy(() -> orderService.placeOrder(accountId, "AAPL", 10, new BigDecimal("200")))
                .isInstanceOf(TradeException.class);

        verify(brokerClient, after(500).never()).sendOrder(any());
    }

    @Test
    @DisplayName("브로커에 끝내 연결하지 못하면 PENDING 그대로, 묶인 돈도 그대로")
    void notDelivered() throws InterruptedException {
        given(brokerClient.sendOrder(any())).willReturn(BrokerResult.NOT_DELIVERED);
        Long accountId = createAccount("10000");

        Long orderId = orderService.placeOrder(accountId, "AAPL", 10, new BigDecimal("200"));

        verify(brokerClient, timeout(5_000)).sendOrder(any());
        Thread.sleep(200);
        assertThat(orderRepository.findById(orderId).orElseThrow().getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(accountRepository.findById(accountId).orElseThrow().getReservedBalance()).isEqualByComparingTo("2000");
    }
}
