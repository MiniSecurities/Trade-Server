package com.sangyunpark99.tradeserver.scheduler;

import com.sangyunpark99.tradeserver.account.Account;
import com.sangyunpark99.tradeserver.account.repository.AccountRepository;
import com.sangyunpark99.tradeserver.broker.BrokerClient;
import com.sangyunpark99.tradeserver.broker.BrokerOrderRequest;
import com.sangyunpark99.tradeserver.broker.BrokerResult;
import com.sangyunpark99.tradeserver.order.entity.OrderStatus;
import com.sangyunpark99.tradeserver.order.repository.OrderRepository;
import com.sangyunpark99.tradeserver.order.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class PendingOrderResendSchedulerTest {

    @Autowired
    private PendingOrderResendScheduler scheduler;

    @Autowired
    private OrderService orderService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OrderRepository orderRepository;

    @MockitoBean
    private BrokerClient brokerClient;

    @BeforeEach
    void setUp() {
        given(brokerClient.isHealthy()).willReturn(true);
    }

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        accountRepository.deleteAll();
    }

    private Long createAccount() {
        return accountRepository.save(new Account(1L, new BigDecimal("1000000"))).getId();
    }

    private Long placeOrder(Long accountId) {
        return orderService.placeOrder(1L, accountId, "AAPL", 1, new BigDecimal("100"));
    }

    private OrderStatus statusOf(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getOrderStatus();
    }

    @Test
    @DisplayName("PENDING 주문을 브로커에 다시 보내고 결과를 반영한다")
    void resendsPendingOrder() {
        given(brokerClient.sendOrder(any())).willReturn(BrokerResult.ACCEPTED);
        Long orderId = placeOrder(createAccount());

        scheduler.resendPendingOrders();

        verify(brokerClient).sendOrder(argThat(r -> r.orderId().equals(orderId)));
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    @DisplayName("PENDING이 아닌 주문은 보내지 않는다")
    void skipsNonPendingOrders() {
        given(brokerClient.sendOrder(any())).willReturn(BrokerResult.ACCEPTED);
        Long accountId = createAccount();
        Long accepted = placeOrder(accountId);
        orderService.markAccepted(accepted);
        Long unknown = placeOrder(accountId);
        orderService.markUnknown(unknown);
        Long sending = placeOrder(accountId);
        orderService.startSending(sending);

        scheduler.resendPendingOrders();

        verify(brokerClient, never()).sendOrder(any());
        assertThat(statusOf(accepted)).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(statusOf(unknown)).isEqualTo(OrderStatus.UNKNOWN);
        assertThat(statusOf(sending)).isEqualTo(OrderStatus.SENDING);
    }

    @Test
    @DisplayName("먼저 접수된 주문부터 id 순서로 보낸다")
    void sendsInIdOrder() {
        given(brokerClient.sendOrder(any())).willReturn(BrokerResult.ACCEPTED);
        Long accountId = createAccount();
        List<Long> orderIds = List.of(placeOrder(accountId), placeOrder(accountId), placeOrder(accountId));

        scheduler.resendPendingOrders();

        ArgumentCaptor<BrokerOrderRequest> captor = ArgumentCaptor.forClass(BrokerOrderRequest.class);
        verify(brokerClient, times(3)).sendOrder(captor.capture());
        assertThat(captor.getAllValues()).extracting(BrokerOrderRequest::orderId).containsExactlyElementsOf(orderIds);
    }

    @Test
    @DisplayName("한 번에 최대 50건만 보내고 나머지는 다음 회차에 보낸다")
    void sendsAtMostFiftyPerRun() {
        given(brokerClient.sendOrder(any())).willReturn(BrokerResult.ACCEPTED);
        Long accountId = createAccount();
        List<Long> orderIds = new ArrayList<>();
        for (int i = 0; i < 55; i++) {
            orderIds.add(placeOrder(accountId));
        }

        scheduler.resendPendingOrders();

        verify(brokerClient, times(50)).sendOrder(any());
        assertThat(statusOf(orderIds.get(49))).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(statusOf(orderIds.get(50))).isEqualTo(OrderStatus.PENDING);

        scheduler.resendPendingOrders();

        verify(brokerClient, times(55)).sendOrder(any());
        assertThat(statusOf(orderIds.get(54))).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    @DisplayName("연결하지 못하면 PENDING으로 돌아가 다음 회차에 다시 보낸다")
    void notDeliveredIsRetriedNextRun() {
        given(brokerClient.sendOrder(any()))
                .willReturn(BrokerResult.NOT_DELIVERED)
                .willReturn(BrokerResult.ACCEPTED);
        Long orderId = placeOrder(createAccount());

        scheduler.resendPendingOrders();
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PENDING);

        scheduler.resendPendingOrders();
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.ACCEPTED);
        verify(brokerClient, times(2)).sendOrder(any());
    }

    @Test
    @DisplayName("한 주문에서 예외가 나도 나머지 주문은 계속 보낸다")
    void continuesAfterOneFailure() {
        Long accountId = createAccount();
        Long first = placeOrder(accountId);
        Long second = placeOrder(accountId);
        given(brokerClient.sendOrder(argThat(r -> r != null && r.orderId().equals(first))))
                .willThrow(new IllegalStateException("예상하지 못한 오류"));
        given(brokerClient.sendOrder(argThat(r -> r != null && r.orderId().equals(second))))
                .willReturn(BrokerResult.ACCEPTED);

        scheduler.resendPendingOrders();

        assertThat(statusOf(second)).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    @DisplayName("브로커 서버가 죽어 있으면 PENDING 주문을 보내지 않고 그대로 둔다")
    void skipsWhenBrokerIsDown() {
        given(brokerClient.isHealthy()).willReturn(false);
        Long orderId = placeOrder(createAccount());

        scheduler.resendPendingOrders();

        verify(brokerClient, never()).sendOrder(any());
        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PENDING);
    }
}
