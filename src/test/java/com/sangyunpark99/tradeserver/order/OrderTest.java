package com.sangyunpark99.tradeserver.order;

import com.sangyunpark99.tradeserver.account.Account;
import com.sangyunpark99.tradeserver.common.exception.ErrorCode;
import com.sangyunpark99.tradeserver.common.exception.TradeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static Order order(long quantity, String limitPrice) {
        return new Order(1L, "AAPL", quantity, new BigDecimal(limitPrice));
    }

    private static void assertError(Executable executable, ErrorCode errorCode) {
        assertThatThrownBy(executable::execute)
                .isInstanceOfSatisfying(TradeException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }

    @Nested
    @DisplayName("주문 생성")
    class Create {

        @Test
        @DisplayName("새 주문은 누적 체결 수량과 금액이 0이고 끝나지 않았다")
        void newOrder() {
            Order order = order(10, "200");

            assertThat(order.getFilledQuantity()).isZero();
            assertThat(order.getFilledAmount()).isEqualByComparingTo("0");
            assertThat(order.isCompleted()).isFalse();
        }

        @Test
        @DisplayName("주문 금액은 주문 수량 × 지정가다")
        void orderAmount() {
            assertThat(order(10, "200").getOrderAmount()).isEqualByComparingTo("2000");
            assertThat(order(3, "199.9999").getOrderAmount()).isEqualByComparingTo("599.9997");
        }

        @Test
        @DisplayName("체결 전 남은 금액은 주문 금액 전부다")
        void remainingBeforeFill() {
            assertThat(order(10, "200").getRemainingAmount()).isEqualByComparingTo("2000");
        }

        @Test
        @DisplayName("종목이 null이거나 비어 있으면 만들 수 없다")
        void rejectBlankSymbol() {
            assertError(() -> new Order(1L, null, 10, new BigDecimal("200")), ErrorCode.INVALID_SYMBOL);
            assertError(() -> new Order(1L, "", 10, new BigDecimal("200")), ErrorCode.INVALID_SYMBOL);
            assertError(() -> new Order(1L, "   ", 10, new BigDecimal("200")), ErrorCode.INVALID_SYMBOL);
        }

        @Test
        @DisplayName("주문 수량이 0 이하면 만들 수 없다")
        void rejectNonPositiveQuantity() {
            assertError(() -> order(0, "200"), ErrorCode.INVALID_QUANTITY);
            assertError(() -> order(-5, "200"), ErrorCode.INVALID_QUANTITY);
        }

        @Test
        @DisplayName("지정가가 null이거나 0 이하면 만들 수 없다")
        void rejectInvalidLimitPrice() {
            assertError(() -> new Order(1L, "AAPL", 10, null), ErrorCode.INVALID_LIMIT_PRICE);
            assertError(() -> order(10, "0"), ErrorCode.INVALID_LIMIT_PRICE);
            assertError(() -> order(10, "-200"), ErrorCode.INVALID_LIMIT_PRICE);
        }
    }

    @Nested
    @DisplayName("체결 반영")
    class ApplyFill {

        @Test
        @DisplayName("5주 × 198이 체결되면 누적 수량 5, 누적 금액 990이다")
        void applyOnce() {
            Order order = order(10, "200");

            order.applyFill(5, new BigDecimal("198"));

            assertThat(order.getFilledQuantity()).isEqualTo(5);
            assertThat(order.getFilledAmount()).isEqualByComparingTo("990");
            assertThat(order.isCompleted()).isFalse();
        }

        @Test
        @DisplayName("3주 198, 5주 199, 2주 200이 체결되면 끝나고 남은 금액은 11이다")
        void applyPartialsUntilCompleted() {
            Order order = order(10, "200");

            order.applyFill(3, new BigDecimal("198"));
            order.applyFill(5, new BigDecimal("199"));
            order.applyFill(2, new BigDecimal("200"));

            assertThat(order.getFilledQuantity()).isEqualTo(10);
            assertThat(order.getFilledAmount()).isEqualByComparingTo("1989");
            assertThat(order.isCompleted()).isTrue();
            assertThat(order.getRemainingAmount()).isEqualByComparingTo("11");
        }

        @Test
        @DisplayName("지정가와 같은 가격에 전부 체결되면 남은 금액은 0이다")
        void applyAtLimitPrice() {
            Order order = order(10, "200");

            order.applyFill(10, new BigDecimal("200"));

            assertThat(order.isCompleted()).isTrue();
            assertThat(order.getRemainingAmount()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("일부만 체결되면 남은 금액은 체결 안 된 몫과 싸게 산 차액의 합이다")
        void remainingAfterPartial() {
            Order order = order(10, "200");

            order.applyFill(5, new BigDecimal("198"));

            assertThat(order.getRemainingAmount()).isEqualByComparingTo("1010");
        }

        @Test
        @DisplayName("누적이 주문 수량을 넘는 체결은 거절하고 값은 그대로다")
        void rejectOverfill() {
            Order order = order(10, "200");
            order.applyFill(8, new BigDecimal("200"));

            assertError(() -> order.applyFill(3, new BigDecimal("200")), ErrorCode.OVERFILL);

            assertThat(order.getFilledQuantity()).isEqualTo(8);
            assertThat(order.getFilledAmount()).isEqualByComparingTo("1600");
        }

        @Test
        @DisplayName("전량 체결이 한 번 더 오면 OVERFILL로 거절한다")
        void rejectDuplicateFullFill() {
            Order order = order(10, "200");
            order.applyFill(10, new BigDecimal("198"));

            assertError(() -> order.applyFill(10, new BigDecimal("198")), ErrorCode.OVERFILL);
        }

        @Test
        @DisplayName("[알려진 구멍] 주문 수량 안의 같은 체결이 두 번 오면 막지 못한다")
        void duplicatePartialFillIsNotDetectedYet() {
            Order order = order(10, "200");

            order.applyFill(3, new BigDecimal("198"));
            order.applyFill(3, new BigDecimal("198"));

            assertThat(order.getFilledQuantity()).isEqualTo(6);
        }

        @Test
        @DisplayName("체결가가 지정가보다 높으면 거절하고 값은 그대로다")
        void rejectPriceAboveLimit() {
            Order order = order(10, "200");

            assertError(() -> order.applyFill(5, new BigDecimal("200.0001")), ErrorCode.FILL_PRICE_EXCEEDS_LIMIT);

            assertThat(order.getFilledQuantity()).isZero();
            assertThat(order.getFilledAmount()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("체결 수량이 0 이하면 거절한다")
        void rejectNonPositiveFillQuantity() {
            Order order = order(10, "200");

            assertError(() -> order.applyFill(0, new BigDecimal("200")), ErrorCode.INVALID_FILL_QUANTITY);
            assertError(() -> order.applyFill(-1, new BigDecimal("200")), ErrorCode.INVALID_FILL_QUANTITY);
        }

        @Test
        @DisplayName("체결가가 null이거나 0 이하면 거절한다")
        void rejectInvalidFillPrice() {
            Order order = order(10, "200");

            assertError(() -> order.applyFill(5, null), ErrorCode.INVALID_FILL_PRICE);
            assertError(() -> order.applyFill(5, new BigDecimal("0")), ErrorCode.INVALID_FILL_PRICE);
            assertError(() -> order.applyFill(5, new BigDecimal("-1")), ErrorCode.INVALID_FILL_PRICE);
        }
    }

    @Nested
    @DisplayName("계좌와 함께: 지정가 매수 한 건")
    class WithAccount {

        @Test
        @DisplayName("주문 금액을 묶고, 체결마다 반영하고, 끝나면 남은 금액을 풀면 묶인 돈이 0이 된다")
        void reserveFillRelease() {
            Account account = new Account(new BigDecimal("10000"));
            Order order = order(10, "200");

            account.reserve(order.getOrderAmount());

            long[] quantities = {3, 5, 2};
            String[] prices = {"198", "199", "200"};
            for (int i = 0; i < quantities.length; i++) {
                BigDecimal price = new BigDecimal(prices[i]);
                order.applyFill(quantities[i], price);
                account.applyFill(price.multiply(BigDecimal.valueOf(quantities[i])));
            }

            assertThat(order.isCompleted()).isTrue();
            account.release(order.getRemainingAmount());

            assertThat(account.getBalance()).isEqualByComparingTo("8011");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("0");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("8011");
        }

        @Test
        @DisplayName("주문 두 개 중 끝난 주문의 남은 금액만 풀면 다른 주문의 묶인 돈은 남는다")
        void releaseOnlyCompletedOrder() {
            Account account = new Account(new BigDecimal("10000"));
            Order a = order(10, "200");
            Order b = order(25, "200");
            account.reserve(a.getOrderAmount());
            account.reserve(b.getOrderAmount());

            a.applyFill(10, new BigDecimal("198"));
            account.applyFill(new BigDecimal("1980"));
            account.release(a.getRemainingAmount());

            assertThat(account.getReservedBalance()).isEqualByComparingTo("5000");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("3020");
        }

        @Test
        @DisplayName("남은 금액 대신 누적 체결 금액을 풀려고 하면 거절된다")
        void releasingFilledAmountIsRejected() {
            Account account = new Account(new BigDecimal("10000"));
            Order order = order(10, "200");
            account.reserve(order.getOrderAmount());
            order.applyFill(10, new BigDecimal("198"));
            account.applyFill(new BigDecimal("1980"));

            assertError(() -> account.release(order.getFilledAmount()), ErrorCode.EXCEED_RESERVED_BALANCE);
        }
    }
}
