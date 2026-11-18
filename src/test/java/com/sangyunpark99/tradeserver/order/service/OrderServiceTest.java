package com.sangyunpark99.tradeserver.order.service;

import com.sangyunpark99.tradeserver.account.Account;
import com.sangyunpark99.tradeserver.account.repository.AccountRepository;
import com.sangyunpark99.tradeserver.common.exception.ErrorCode;
import com.sangyunpark99.tradeserver.common.exception.TradeException;
import com.sangyunpark99.tradeserver.order.Order;
import com.sangyunpark99.tradeserver.order.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OrderServiceTest {

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

    private Long createAccount(String balance) {
        return accountRepository.save(new Account(new BigDecimal(balance))).getId();
    }

    private void assertTradeException(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(TradeException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }

    @Nested
    @DisplayName("지정가 매수 주문")
    class PlaceOrder {

        @Test
        @DisplayName("주문 금액만큼 계좌의 돈이 묶이고 주문이 저장된다")
        void reservesOrderAmountAndSavesOrder() {
            Long accountId = createAccount("10000");

            Long orderId = orderService.placeOrder(accountId, "AAPL", 10, new BigDecimal("200"));

            Account account = accountRepository.findById(accountId).orElseThrow();
            assertThat(account.getBalance()).isEqualByComparingTo("10000");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("2000");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("8000");

            Order order = orderRepository.findById(orderId).orElseThrow();
            assertThat(order.getAccountId()).isEqualTo(accountId);
            assertThat(order.getSymbol()).isEqualTo("AAPL");
            assertThat(order.getQuantity()).isEqualTo(10);
            assertThat(order.getLimitPrice()).isEqualByComparingTo("200");
            assertThat(order.getFilledQuantity()).isZero();
            assertThat(order.getFilledAmount()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("주문 가능 금액과 정확히 같은 금액도 주문된다")
        void placesOrderEqualToAvailableBalance() {
            Long accountId = createAccount("2000");

            orderService.placeOrder(accountId, "AAPL", 10, new BigDecimal("200"));

            Account account = accountRepository.findById(accountId).orElseThrow();
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("두 번 주문하면 묶인 돈이 더해진다")
        void accumulatesReservedBalance() {
            Long accountId = createAccount("10000");

            orderService.placeOrder(accountId, "AAPL", 10, new BigDecimal("200"));
            orderService.placeOrder(accountId, "AAPL", 5, new BigDecimal("300"));

            Account account = accountRepository.findById(accountId).orElseThrow();
            assertThat(account.getReservedBalance()).isEqualByComparingTo("3500");
            assertThat(orderRepository.count()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("주문 거절")
    class RejectOrder {

        @Test
        @DisplayName("주문 가능 금액이 부족하면 거절되고 돈도 묶이지 않고 주문도 남지 않는다")
        void rejectsWhenAvailableBalanceIsInsufficient() {
            Long accountId = createAccount("1999");

            assertTradeException(
                    () -> orderService.placeOrder(accountId, "AAPL", 10, new BigDecimal("200")),
                    ErrorCode.INSUFFICIENT_AVAILABLE_BALANCE);

            Account account = accountRepository.findById(accountId).orElseThrow();
            assertThat(account.getReservedBalance()).isEqualByComparingTo("0");
            assertThat(orderRepository.count()).isZero();
        }

        @Test
        @DisplayName("이미 묶인 돈 때문에 주문 가능 금액이 부족하면 거절된다")
        void rejectsWhenAlreadyReserved() {
            Long accountId = createAccount("10000");
            orderService.placeOrder(accountId, "AAPL", 40, new BigDecimal("200"));

            assertTradeException(
                    () -> orderService.placeOrder(accountId, "AAPL", 11, new BigDecimal("200")),
                    ErrorCode.INSUFFICIENT_AVAILABLE_BALANCE);

            Account account = accountRepository.findById(accountId).orElseThrow();
            assertThat(account.getReservedBalance()).isEqualByComparingTo("8000");
            assertThat(orderRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("없는 계좌면 NOT_FOUND_ACCOUNT로 거절된다")
        void rejectsWhenAccountNotFound() {
            assertTradeException(
                    () -> orderService.placeOrder(999_999L, "AAPL", 10, new BigDecimal("200")),
                    ErrorCode.NOT_FOUND_ACCOUNT);

            assertThat(orderRepository.count()).isZero();
        }

        @Test
        @DisplayName("수량이 잘못되면 INVALID_QUANTITY로 거절되고 돈이 묶이지 않는다")
        void rejectsInvalidQuantity() {
            Long accountId = createAccount("10000");

            assertTradeException(
                    () -> orderService.placeOrder(accountId, "AAPL", -1, new BigDecimal("200")),
                    ErrorCode.INVALID_QUANTITY);

            Account account = accountRepository.findById(accountId).orElseThrow();
            assertThat(account.getReservedBalance()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("지정가가 잘못되면 INVALID_LIMIT_PRICE로 거절되고 돈이 묶이지 않는다")
        void rejectsInvalidLimitPrice() {
            Long accountId = createAccount("10000");

            assertTradeException(
                    () -> orderService.placeOrder(accountId, "AAPL", 10, BigDecimal.ZERO),
                    ErrorCode.INVALID_LIMIT_PRICE);

            Account account = accountRepository.findById(accountId).orElseThrow();
            assertThat(account.getReservedBalance()).isEqualByComparingTo("0");
        }
    }
}
