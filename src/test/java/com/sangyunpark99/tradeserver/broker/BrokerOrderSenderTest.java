package com.sangyunpark99.tradeserver.broker;

import com.sangyunpark99.tradeserver.account.Account;
import com.sangyunpark99.tradeserver.account.repository.AccountRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class BrokerOrderSenderTest {

    @Autowired
    private BrokerOrderSender sender;

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

    private Long placeOrder() {
        Long accountId = accountRepository.save(new Account(new BigDecimal("10000"))).getId();
        return orderService.placeOrder(accountId, "AAPL", 10, new BigDecimal("200"));
    }

    @Test
    @DisplayName("같은 주문을 순서대로 두 번 보내도 브로커는 한 번만 호출된다")
    void sendTwiceCallsBrokerOnce() {
        given(brokerClient.sendOrder(any())).willReturn(BrokerResult.ACCEPTED);
        Long orderId = placeOrder();

        sender.send(orderId);
        sender.send(orderId);

        verify(brokerClient, times(1)).sendOrder(any());
        assertThat(orderRepository.findById(orderId).orElseThrow().getOrderStatus()).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    @DisplayName("다른 스레드가 이미 SENDING으로 가져간 주문은 브로커에 보내지 않는다")
    void doesNotSendOrderAlreadySending() {
        Long orderId = placeOrder();
        orderService.startSending(orderId);

        sender.send(orderId);

        verify(brokerClient, never()).sendOrder(any());
        assertThat(orderRepository.findById(orderId).orElseThrow().getOrderStatus()).isEqualTo(OrderStatus.SENDING);
    }
}
