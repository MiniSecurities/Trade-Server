package com.sangyunpark99.tradeserver.notification;

import com.sangyunpark99.tradeserver.account.Account;
import com.sangyunpark99.tradeserver.account.repository.AccountRepository;
import com.sangyunpark99.tradeserver.fill.repository.FillRepository;
import com.sangyunpark99.tradeserver.notification.service.NotificationService;
import com.sangyunpark99.tradeserver.order.repository.OrderRepository;
import com.sangyunpark99.tradeserver.order.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class FillNotificationListenerTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private FillRepository fillRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private NotificationService notificationService;

    @AfterEach
    void tearDown() {
        fillRepository.deleteAll();
        orderRepository.deleteAll();
        accountRepository.deleteAll();
    }

    private Long placeOrder() {
        Long accountId = accountRepository.save(new Account(1L, new BigDecimal("10000"))).getId();
        return orderService.placeOrder(1L, accountId, "KAKAO", 10, new BigDecimal("200"));
    }

    @Test
    @DisplayName("체결이 커밋되면 계좌 주인에게 체결 내용으로 알림을 보낸다")
    void sendsFillAfterCommit() {
        Long orderId = placeOrder();

        orderService.applyFill("fill-1", orderId, 3, new BigDecimal("198"));

        ArgumentCaptor<FillAppliedEvent> captor = ArgumentCaptor.forClass(FillAppliedEvent.class);
        verify(notificationService, times(1)).sendFill(captor.capture());
        FillAppliedEvent event = captor.getValue();
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.fillId()).isEqualTo("fill-1");
        assertThat(event.orderId()).isEqualTo(orderId);
        assertThat(event.symbol()).isEqualTo("KAKAO");
        assertThat(event.quantity()).isEqualTo(3);
        assertThat(event.price()).isEqualByComparingTo("198");
        assertThat(event.filledAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 체결 ID가 다시 오면 알림을 한 번만 보낸다")
    void sendsOnceForDuplicateFillId() {
        Long orderId = placeOrder();

        orderService.applyFill("fill-1", orderId, 3, new BigDecimal("198"));
        orderService.applyFill("fill-1", orderId, 3, new BigDecimal("198"));

        verify(notificationService, times(1)).sendFill(any());
    }

    @Test
    @DisplayName("체결 트랜잭션이 롤백되면 알림을 보내지 않는다")
    void doesNotSendWhenRolledBack() {
        Long orderId = placeOrder();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            orderService.applyFill("fill-1", orderId, 3, new BigDecimal("198"));
            status.setRollbackOnly();
        });

        verify(notificationService, never()).sendFill(any());
        assertThat(fillRepository.count()).isZero();
    }
}
