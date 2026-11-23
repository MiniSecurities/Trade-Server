package com.sangyunpark99.tradeserver.order;

import com.sangyunpark99.tradeserver.account.Account;
import com.sangyunpark99.tradeserver.common.exception.ErrorCode;
import com.sangyunpark99.tradeserver.common.exception.TradeException;
import com.sangyunpark99.tradeserver.order.entity.Order;
import com.sangyunpark99.tradeserver.order.entity.OrderStatus;
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

    @Nested
    @DisplayName("주문 상태")
    class Status {

        @Test
        @DisplayName("새 주문은 PENDING이다")
        void newOrderIsPending() {
            assertThat(order(10, "200").getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("일부만 체결되면 PARTIALLY_FILLED다")
        void partialFill() {
            Order order = order(10, "200");

            order.applyFill(3, new BigDecimal("198"));

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        }

        @Test
        @DisplayName("나눠서 전부 체결되면 FILLED다")
        void filledInParts() {
            Order order = order(10, "200");

            order.applyFill(3, new BigDecimal("198"));
            order.applyFill(7, new BigDecimal("199"));

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.FILLED);
        }

        @Test
        @DisplayName("한 번에 전부 체결되면 FILLED다")
        void filledAtOnce() {
            Order order = order(10, "200");

            order.applyFill(10, new BigDecimal("200"));

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.FILLED);
        }

        @Test
        @DisplayName("PENDING 상태에서도 체결은 반영된다 (접수 확인보다 체결이 먼저 온 경우)")
        void fillOnPendingIsApplied() {
            Order order = order(10, "200");

            order.applyFill(3, new BigDecimal("198"));

            assertThat(order.getFilledQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("UNKNOWN 상태에서 체결이 오면 반영된다 (체결이 접수의 증거)")
        void fillOnUnknownIsApplied() {
            Order order = order(10, "200");
            order.markUnknown();

            order.applyFill(3, new BigDecimal("198"));

            assertThat(order.getFilledQuantity()).isEqualTo(3);
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        }

        @Test
        @DisplayName("REJECTED 주문에 체결이 오면 거절되고 아무것도 바뀌지 않는다")
        void fillOnRejectedIsRefused() {
            Order order = order(10, "200");
            order.reject();

            assertError(() -> order.applyFill(3, new BigDecimal("198")), ErrorCode.FILL_ON_REJECTED_ORDER);

            assertThat(order.getFilledQuantity()).isZero();
            assertThat(order.getFilledAmount()).isEqualByComparingTo("0");
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.REJECTED);
        }

        @Test
        @DisplayName("체결이 거절되면 상태도 바뀌지 않는다")
        void statusUnchangedOnRejectedFill() {
            Order order = order(10, "200");
            order.applyFill(3, new BigDecimal("198"));

            assertError(() -> order.applyFill(8, new BigDecimal("198")), ErrorCode.OVERFILL);

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        }
    }

    @Nested
    @DisplayName("결과 모름과 거절")
    class UnknownAndReject {

        @Test
        @DisplayName("PENDING 주문을 결과 모름으로 바꾸면 UNKNOWN이다")
        void pendingToUnknown() {
            Order order = order(10, "200");

            order.markUnknown();

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.UNKNOWN);
        }

        @Test
        @DisplayName("체결이 먼저 와서 PARTIALLY_FILLED면 결과 모름으로 바꾸지 않는다")
        void partiallyFilledStaysOnUnknown() {
            Order order = order(10, "200");
            order.applyFill(3, new BigDecimal("198"));

            order.markUnknown();

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        }

        @Test
        @DisplayName("PENDING 주문을 거절하면 REJECTED다")
        void pendingToRejected() {
            Order order = order(10, "200");

            order.reject();

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.REJECTED);
        }

        @Test
        @DisplayName("UNKNOWN 주문도 거절될 수 있다 (재전송했더니 브로커가 거절)")
        void unknownToRejected() {
            Order order = order(10, "200");
            order.markUnknown();

            order.reject();

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.REJECTED);
        }

        @Test
        @DisplayName("이미 체결된 주문은 거절할 수 없다")
        void cannotRejectFilledOrder() {
            Order order = order(10, "200");
            order.applyFill(3, new BigDecimal("198"));

            assertError(order::reject, ErrorCode.INVALID_ORDER_STATUS);
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        }
    }

    @Nested
    @DisplayName("브로커 접수 확인")
    class Accept {

        @Test
        @DisplayName("PENDING 주문은 ACCEPTED가 된다")
        void pendingToAccepted() {
            Order order = order(10, "200");

            order.accept();

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.ACCEPTED);
        }

        @Test
        @DisplayName("UNKNOWN 주문도 접수가 확인되면 ACCEPTED가 된다")
        void unknownToAccepted() {
            Order order = order(10, "200");
            order.markUnknown();

            order.accept();

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.ACCEPTED);
        }

        @Test
        @DisplayName("체결이 먼저 와서 PARTIALLY_FILLED면 접수 확인이 와도 뒤로 돌아가지 않는다")
        void partiallyFilledStays() {
            Order order = order(10, "200");
            order.applyFill(3, new BigDecimal("198"));

            order.accept();

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        }

        @Test
        @DisplayName("이미 FILLED면 접수 확인이 와도 그대로다")
        void filledStays() {
            Order order = order(10, "200");
            order.applyFill(10, new BigDecimal("198"));

            order.accept();

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.FILLED);
        }

        @Test
        @DisplayName("접수 확인이 두 번 와도 ACCEPTED 그대로다")
        void acceptTwice() {
            Order order = order(10, "200");
            order.accept();

            order.accept();

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.ACCEPTED);
        }

        @Test
        @DisplayName("거절된 주문에 접수 확인이 오면 모순이라 예외다")
        void rejectedThenAcceptIsContradiction() {
            Order order = order(10, "200");
            order.reject();

            assertError(order::accept, ErrorCode.INVALID_ORDER_STATUS);
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.REJECTED);
        }
    }

    @Nested
    @DisplayName("브로커 전송 상태 (SENDING)")
    class Sending {

        private Order sendingOrder() {
            Order order = order(10, "200");
            order.startSending();
            return order;
        }

        @Test
        @DisplayName("PENDING 주문을 전송 시작하면 SENDING이다")
        void pendingToSending() {
            assertThat(sendingOrder().getOrderStatus()).isEqualTo(OrderStatus.SENDING);
        }

        @Test
        @DisplayName("이미 ACCEPTED인 주문은 전송 시작해도 그대로다")
        void acceptedStaysOnSend() {
            Order order = sendingOrder();
            order.accept();

            order.startSending();

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.ACCEPTED);
        }

        @Test
        @DisplayName("이미 체결이 들어온 주문은 전송 시작해도 그대로다")
        void partiallyFilledStaysOnSend() {
            Order order = sendingOrder();
            order.applyFill(3, new BigDecimal("198"));

            order.startSending();

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        }

        @Test
        @DisplayName("SENDING 주문을 되돌리면 PENDING이다 (연결 실패)")
        void sendingToPending() {
            Order order = sendingOrder();

            order.returnToPending();

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("체결된 주문은 되돌려도 PENDING으로 돌아가지 않는다")
        void filledDoesNotReturnToPending() {
            Order order = sendingOrder();
            order.applyFill(10, new BigDecimal("198"));

            order.returnToPending();

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.FILLED);
        }

        @Test
        @DisplayName("ACCEPTED 주문은 되돌려도 PENDING으로 돌아가지 않는다")
        void acceptedDoesNotReturnToPending() {
            Order order = sendingOrder();
            order.accept();

            order.returnToPending();

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.ACCEPTED);
        }

        @Test
        @DisplayName("SENDING 주문이 접수 확인을 받으면 ACCEPTED다")
        void sendingToAccepted() {
            Order order = sendingOrder();

            order.accept();

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.ACCEPTED);
        }

        @Test
        @DisplayName("SENDING 주문의 결과를 모르면 UNKNOWN이다")
        void sendingToUnknown() {
            Order order = sendingOrder();

            order.markUnknown();

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.UNKNOWN);
        }

        @Test
        @DisplayName("SENDING 주문이 거절되면 REJECTED다")
        void sendingToRejected() {
            Order order = sendingOrder();

            order.reject();

            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.REJECTED);
        }

        @Test
        @DisplayName("SENDING 중에 체결이 먼저 오면 반영되고 PARTIALLY_FILLED다")
        void fillWhileSending() {
            Order order = sendingOrder();

            order.applyFill(3, new BigDecimal("198"));

            assertThat(order.getFilledQuantity()).isEqualTo(3);
            assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
        }
    }

    @Nested
    @DisplayName("평균 체결가")
    class AveragePrice {

        @Test
        @DisplayName("체결이 없으면 평균 체결가는 null이다")
        void nullWhenNoFill() {
            assertThat(order(10, "200").getAveragePrice()).isNull();
        }

        @Test
        @DisplayName("평균 체결가는 누적 체결 금액 ÷ 누적 체결 수량이다")
        void averageOfFills() {
            Order order = order(10, "200");
            order.applyFill(3, new BigDecimal("198"));
            order.applyFill(2, new BigDecimal("200"));

            assertThat(order.getAveragePrice()).isEqualByComparingTo("198.8");
        }

        @Test
        @DisplayName("나누어떨어지지 않으면 소수 넷째 자리에서 반올림한다")
        void roundsToFourDecimals() {
            Order order = order(3, "101");
            order.applyFill(1, new BigDecimal("100"));
            order.applyFill(2, new BigDecimal("101"));

            assertThat(order.getAveragePrice()).isEqualByComparingTo("100.6667");
        }
    }
}
