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

    @Nested
    @DisplayName("체결 반영")
    class ApplyFill {

        @Test
        @DisplayName("부분 체결이면 체결 금액만 현금과 묶인 돈에서 빠지고 나머지는 묶여 있다")
        void partialFill() {
            Long accountId = createAccount("10000");
            Long orderId = orderService.placeOrder(accountId, "AAPL", 10, new BigDecimal("200"));

            orderService.applyFill(orderId, 3, new BigDecimal("198"));

            Account account = accountRepository.findById(accountId).orElseThrow();
            assertThat(account.getBalance()).isEqualByComparingTo("9406");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("1406");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("8000");

            Order order = orderRepository.findById(orderId).orElseThrow();
            assertThat(order.getFilledQuantity()).isEqualTo(3);
            assertThat(order.getFilledAmount()).isEqualByComparingTo("594");
            assertThat(order.isCompleted()).isFalse();
        }

        @Test
        @DisplayName("지정가보다 싸게 나눠 체결돼 완료되면 남은 금액이 풀린다")
        void completedBelowLimitReleasesRemaining() {
            Long accountId = createAccount("10000");
            Long orderId = orderService.placeOrder(accountId, "AAPL", 10, new BigDecimal("200"));

            orderService.applyFill(orderId, 3, new BigDecimal("198"));
            orderService.applyFill(orderId, 5, new BigDecimal("199"));
            orderService.applyFill(orderId, 2, new BigDecimal("200"));

            Account account = accountRepository.findById(accountId).orElseThrow();
            assertThat(account.getBalance()).isEqualByComparingTo("8011");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("0");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("8011");

            Order order = orderRepository.findById(orderId).orElseThrow();
            assertThat(order.isCompleted()).isTrue();
            assertThat(order.getFilledAmount()).isEqualByComparingTo("1989");
        }

        @Test
        @DisplayName("지정가에 전량 체결되면 남은 금액이 0이어도 체결이 반영된다")
        void completedAtLimitPriceIsApplied() {
            Long accountId = createAccount("10000");
            Long orderId = orderService.placeOrder(accountId, "AAPL", 10, new BigDecimal("200"));

            orderService.applyFill(orderId, 10, new BigDecimal("200"));

            Account account = accountRepository.findById(accountId).orElseThrow();
            assertThat(account.getBalance()).isEqualByComparingTo("8000");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("0");

            Order order = orderRepository.findById(orderId).orElseThrow();
            assertThat(order.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("한 주문이 완료돼도 같은 계좌의 다른 주문 묶인 돈은 그대로다")
        void releasesOnlyCompletedOrder() {
            Long accountId = createAccount("10000");
            Long orderA = orderService.placeOrder(accountId, "AAPL", 10, new BigDecimal("200"));
            orderService.placeOrder(accountId, "AAPL", 5, new BigDecimal("1000"));

            orderService.applyFill(orderA, 10, new BigDecimal("190"));

            Account account = accountRepository.findById(accountId).orElseThrow();
            assertThat(account.getBalance()).isEqualByComparingTo("8100");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("5000");
        }

        @Test
        @DisplayName("브로커가 없는 주문으로 체결을 통보하면 FILL_ORDER_NOT_FOUND로 거절된다")
        void rejectsWhenOrderNotFound() {
            assertTradeException(
                    () -> orderService.applyFill(999_999L, 1, new BigDecimal("200")),
                    ErrorCode.FILL_ORDER_NOT_FOUND);
        }

        @Test
        @DisplayName("체결가가 지정가보다 높으면 거절되고 주문과 계좌 모두 바뀌지 않는다")
        void rejectsFillPriceAboveLimitWithoutChange() {
            Long accountId = createAccount("10000");
            Long orderId = orderService.placeOrder(accountId, "AAPL", 10, new BigDecimal("200"));

            assertTradeException(
                    () -> orderService.applyFill(orderId, 3, new BigDecimal("201")),
                    ErrorCode.FILL_PRICE_EXCEEDS_LIMIT);

            assertUnchanged(accountId, orderId);
        }

        @Test
        @DisplayName("주문 수량을 넘는 체결은 거절되고 주문과 계좌 모두 바뀌지 않는다")
        void rejectsOverfillWithoutChange() {
            Long accountId = createAccount("10000");
            Long orderId = orderService.placeOrder(accountId, "AAPL", 10, new BigDecimal("200"));

            assertTradeException(
                    () -> orderService.applyFill(orderId, 11, new BigDecimal("200")),
                    ErrorCode.OVERFILL);

            assertUnchanged(accountId, orderId);
        }

        @Test
        @DisplayName("완료된 주문에 체결이 또 오면 거절되고 계좌는 바뀌지 않는다")
        void rejectsFillAfterCompleted() {
            Long accountId = createAccount("10000");
            Long orderId = orderService.placeOrder(accountId, "AAPL", 10, new BigDecimal("200"));
            orderService.applyFill(orderId, 10, new BigDecimal("190"));

            assertTradeException(
                    () -> orderService.applyFill(orderId, 1, new BigDecimal("190")),
                    ErrorCode.OVERFILL);

            Account account = accountRepository.findById(accountId).orElseThrow();
            assertThat(account.getBalance()).isEqualByComparingTo("8100");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("0");
        }

        private void assertUnchanged(Long accountId, Long orderId) {
            Account account = accountRepository.findById(accountId).orElseThrow();
            assertThat(account.getBalance()).isEqualByComparingTo("10000");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("2000");

            Order order = orderRepository.findById(orderId).orElseThrow();
            assertThat(order.getFilledQuantity()).isZero();
            assertThat(order.getFilledAmount()).isEqualByComparingTo("0");
        }
    }
}
