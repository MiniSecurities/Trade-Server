package com.sangyunpark99.tradeserver.account;

import com.sangyunpark99.tradeserver.common.exception.ErrorCode;
import com.sangyunpark99.tradeserver.common.exception.TradeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    @Nested
    @DisplayName("계좌 생성")
    class Create {

        @Test
        @DisplayName("현금 0원으로 계좌를 만들 수 있다")
        void zeroBalance() {
            Account account = new Account(BigDecimal.ZERO);

            assertThat(account.getBalance()).isEqualByComparingTo("0");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("0");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("새 계좌는 묶인 돈이 0이라 현금 전부를 주문에 쓸 수 있다")
        void availableEqualsBalance() {
            Account account = new Account(new BigDecimal("10000"));

            assertThat(account.getAvailableBalance()).isEqualByComparingTo("10000");
        }

        @Test
        @DisplayName("현금이 음수면 계좌를 만들 수 없다")
        void negativeBalance() {
            assertThatThrownBy(() -> new Account(new BigDecimal("-1")))
                    .isInstanceOfSatisfying(TradeException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_BALANCE));
        }

        @Test
        @DisplayName("현금이 null이면 계좌를 만들 수 없다")
        void nullBalance() {
            assertThatThrownBy(() -> new Account(null))
                    .isInstanceOfSatisfying(TradeException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_BALANCE));
        }
    }

    @Nested
    @DisplayName("묶기")
    class Reserve {

        @Test
        @DisplayName("현금 10,000에서 2,000을 묶으면 주문 가능 금액은 8,000이다")
        void reserveOnce() {
            Account account = new Account(new BigDecimal("10000"));

            account.reserve(new BigDecimal("2000"));

            assertThat(account.getBalance()).isEqualByComparingTo("10000");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("2000");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("8000");
        }

        @Test
        @DisplayName("2,000을 묶은 뒤 3,000을 더 묶으면 묶인 돈은 5,000, 주문 가능 금액은 5,000이다")
        void reserveTwiceAccumulates() {
            Account account = new Account(new BigDecimal("10000"));

            account.reserve(new BigDecimal("2000"));
            account.reserve(new BigDecimal("3000"));

            assertThat(account.getReservedBalance()).isEqualByComparingTo("5000");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("5000");
        }

        @Test
        @DisplayName("주문 가능 금액 전부를 묶을 수 있다")
        void reserveExactlyAvailable() {
            Account account = new Account(new BigDecimal("10000"));

            account.reserve(new BigDecimal("10000"));

            assertThat(account.getAvailableBalance()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("이미 묶인 돈을 빼고 남은 금액보다 크면 거절하고, 묶인 돈은 그대로다")
        void rejectWhenExceedsAvailable() {
            Account account = new Account(new BigDecimal("10000"));
            account.reserve(new BigDecimal("2000"));

            assertThatThrownBy(() -> account.reserve(new BigDecimal("9000")))
                    .isInstanceOfSatisfying(TradeException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_AVAILABLE_BALANCE));

            assertThat(account.getReservedBalance()).isEqualByComparingTo("2000");
        }

        @Test
        @DisplayName("현금의 절반보다 커도 주문 가능 금액 이하면 묶을 수 있다")
        void reserveMoreThanHalf() {
            Account account = new Account(new BigDecimal("10000"));

            account.reserve(new BigDecimal("6000"));

            assertThat(account.getAvailableBalance()).isEqualByComparingTo("4000");
        }

        @Test
        @DisplayName("소수점 4자리 금액도 정확히 묶는다")
        void reserveFractional() {
            Account account = new Account(new BigDecimal("100"));

            account.reserve(new BigDecimal("0.0001"));

            assertThat(account.getAvailableBalance()).isEqualByComparingTo("99.9999");
        }

        @Test
        @DisplayName("0원을 묶으면 거절한다")
        void rejectZero() {
            Account account = new Account(new BigDecimal("10000"));

            assertThatThrownBy(() -> account.reserve(BigDecimal.ZERO))
                    .isInstanceOfSatisfying(TradeException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_AMOUNT));
        }

        @Test
        @DisplayName("음수를 묶으면 거절한다")
        void rejectNegative() {
            Account account = new Account(new BigDecimal("10000"));

            assertThatThrownBy(() -> account.reserve(new BigDecimal("-1")))
                    .isInstanceOfSatisfying(TradeException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_AMOUNT));
        }

        @Test
        @DisplayName("null을 묶으면 거절한다")
        void rejectNull() {
            Account account = new Account(new BigDecimal("10000"));

            assertThatThrownBy(() -> account.reserve(null))
                    .isInstanceOfSatisfying(TradeException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_AMOUNT));
        }
    }

    @Nested
    @DisplayName("체결 반영")
    class ApplyFill {

        @Test
        @DisplayName("2,000을 묶고 1,980이 체결되면 현금과 묶인 돈에서 같은 금액이 빠지고 주문 가능 금액은 그대로다")
        void applyFull() {
            Account account = new Account(new BigDecimal("10000"));
            account.reserve(new BigDecimal("2000"));

            account.applyFill(new BigDecimal("1980"));

            assertThat(account.getBalance()).isEqualByComparingTo("8020");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("20");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("8000");
        }

        @Test
        @DisplayName("부분 체결이 여러 번 와도 누적해서 빠진다")
        void applyPartialTwice() {
            Account account = new Account(new BigDecimal("10000"));
            account.reserve(new BigDecimal("2000"));

            account.applyFill(new BigDecimal("594"));
            account.applyFill(new BigDecimal("995"));

            assertThat(account.getBalance()).isEqualByComparingTo("8411");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("411");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("8000");
        }

        @Test
        @DisplayName("묶인 돈 전부를 체결로 반영할 수 있다")
        void applyExactlyReserved() {
            Account account = new Account(new BigDecimal("10000"));
            account.reserve(new BigDecimal("2000"));

            account.applyFill(new BigDecimal("2000"));

            assertThat(account.getBalance()).isEqualByComparingTo("8000");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("묶인 돈보다 큰 금액은 거절하고, 현금과 묶인 돈은 그대로다")
        void rejectWhenExceedsReserved() {
            Account account = new Account(new BigDecimal("10000"));
            account.reserve(new BigDecimal("2000"));

            assertThatThrownBy(() -> account.applyFill(new BigDecimal("2100")))
                    .isInstanceOfSatisfying(TradeException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXCEED_RESERVED_BALANCE));

            assertThat(account.getBalance()).isEqualByComparingTo("10000");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("2000");
        }

        @Test
        @DisplayName("같은 체결이 두 번 와서 묶인 돈을 넘으면 두 번째는 거절한다")
        void rejectDuplicateFillOverReserved() {
            Account account = new Account(new BigDecimal("10000"));
            account.reserve(new BigDecimal("2000"));
            account.applyFill(new BigDecimal("1980"));

            assertThatThrownBy(() -> account.applyFill(new BigDecimal("1980")))
                    .isInstanceOfSatisfying(TradeException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXCEED_RESERVED_BALANCE));

            assertThat(account.getBalance()).isEqualByComparingTo("8020");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("20");
        }

        @Test
        @DisplayName("묶은 돈이 없으면 체결을 반영할 수 없다")
        void rejectWithoutReserve() {
            Account account = new Account(new BigDecimal("10000"));

            assertThatThrownBy(() -> account.applyFill(new BigDecimal("1")))
                    .isInstanceOfSatisfying(TradeException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXCEED_RESERVED_BALANCE));

            assertThat(account.getBalance()).isEqualByComparingTo("10000");
        }

        @Test
        @DisplayName("소수점 4자리 체결 금액도 정확히 빠진다")
        void applyFractional() {
            Account account = new Account(new BigDecimal("100"));
            account.reserve(new BigDecimal("10"));

            account.applyFill(new BigDecimal("9.9999"));

            assertThat(account.getBalance()).isEqualByComparingTo("90.0001");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("0.0001");
        }

        @Test
        @DisplayName("0원 체결을 반영하면 거절한다")
        void rejectZero() {
            Account account = new Account(new BigDecimal("10000"));
            account.reserve(new BigDecimal("2000"));

            assertThatThrownBy(() -> account.applyFill(BigDecimal.ZERO))
                    .isInstanceOfSatisfying(TradeException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_AMOUNT));
        }

        @Test
        @DisplayName("음수 체결을 반영하면 거절한다")
        void rejectNegative() {
            Account account = new Account(new BigDecimal("10000"));
            account.reserve(new BigDecimal("2000"));

            assertThatThrownBy(() -> account.applyFill(new BigDecimal("-1")))
                    .isInstanceOfSatisfying(TradeException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_AMOUNT));
        }

        @Test
        @DisplayName("null 체결을 반영하면 거절한다")
        void rejectNull() {
            Account account = new Account(new BigDecimal("10000"));
            account.reserve(new BigDecimal("2000"));

            assertThatThrownBy(() -> account.applyFill(null))
                    .isInstanceOfSatisfying(TradeException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_AMOUNT));
        }
    }

    @Nested
    @DisplayName("풀기")
    class Release {

        @Test
        @DisplayName("8,000을 묶고 7,900이 체결된 뒤 남은 100을 풀면 현금은 그대로, 주문 가능 금액만 늘어난다")
        void releaseRemainderAfterFill() {
            Account account = new Account(new BigDecimal("10000"));
            account.reserve(new BigDecimal("8000"));
            account.applyFill(new BigDecimal("7900"));

            account.release(new BigDecimal("100"));

            assertThat(account.getBalance()).isEqualByComparingTo("2100");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("0");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("2100");
        }

        @Test
        @DisplayName("체결 없이 묶은 돈 전부를 풀면 처음 상태로 돌아간다")
        void releaseAllWithoutFill() {
            Account account = new Account(new BigDecimal("10000"));
            account.reserve(new BigDecimal("2000"));

            account.release(new BigDecimal("2000"));

            assertThat(account.getBalance()).isEqualByComparingTo("10000");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("0");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("10000");
        }

        @Test
        @DisplayName("부분 체결 뒤 남은 주문을 끝내면 남은 묶인 돈을 풀 수 있다")
        void releaseAfterPartialFill() {
            Account account = new Account(new BigDecimal("10000"));
            account.reserve(new BigDecimal("2000"));
            account.applyFill(new BigDecimal("1000"));

            account.release(new BigDecimal("1000"));

            assertThat(account.getBalance()).isEqualByComparingTo("9000");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("0");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("9000");
        }

        @Test
        @DisplayName("주문 두 개 중 하나만 풀면 다른 주문의 묶인 돈은 남는다")
        void releaseOneOfTwoOrders() {
            Account account = new Account(new BigDecimal("10000"));
            account.reserve(new BigDecimal("2000"));
            account.reserve(new BigDecimal("3000"));

            account.release(new BigDecimal("2000"));

            assertThat(account.getReservedBalance()).isEqualByComparingTo("3000");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("7000");
        }

        @Test
        @DisplayName("묶인 돈보다 큰 금액을 풀면 거절하고, 현금과 묶인 돈은 그대로다")
        void rejectWhenExceedsReserved() {
            Account account = new Account(new BigDecimal("10000"));
            account.reserve(new BigDecimal("8000"));
            account.applyFill(new BigDecimal("7900"));

            assertThatThrownBy(() -> account.release(new BigDecimal("150")))
                    .isInstanceOfSatisfying(TradeException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXCEED_RESERVED_BALANCE));

            assertThat(account.getBalance()).isEqualByComparingTo("2100");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("100");
        }

        @Test
        @DisplayName("같은 금액을 두 번 풀면 두 번째는 거절한다")
        void rejectDoubleRelease() {
            Account account = new Account(new BigDecimal("10000"));
            account.reserve(new BigDecimal("2000"));
            account.release(new BigDecimal("2000"));

            assertThatThrownBy(() -> account.release(new BigDecimal("2000")))
                    .isInstanceOfSatisfying(TradeException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EXCEED_RESERVED_BALANCE));

            assertThat(account.getBalance()).isEqualByComparingTo("10000");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("소수점 4자리 금액도 정확히 푼다")
        void releaseFractional() {
            Account account = new Account(new BigDecimal("100"));
            account.reserve(new BigDecimal("10"));
            account.applyFill(new BigDecimal("9.9999"));

            account.release(new BigDecimal("0.0001"));

            assertThat(account.getReservedBalance()).isEqualByComparingTo("0");
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("90.0001");
        }

        @Test
        @DisplayName("0원을 풀면 거절한다")
        void rejectZero() {
            Account account = new Account(new BigDecimal("10000"));
            account.reserve(new BigDecimal("2000"));

            assertThatThrownBy(() -> account.release(BigDecimal.ZERO))
                    .isInstanceOfSatisfying(TradeException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_AMOUNT));
        }

        @Test
        @DisplayName("음수를 풀면 거절한다")
        void rejectNegative() {
            Account account = new Account(new BigDecimal("10000"));
            account.reserve(new BigDecimal("2000"));

            assertThatThrownBy(() -> account.release(new BigDecimal("-1")))
                    .isInstanceOfSatisfying(TradeException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_AMOUNT));
        }

        @Test
        @DisplayName("null을 풀면 거절한다")
        void rejectNull() {
            Account account = new Account(new BigDecimal("10000"));
            account.reserve(new BigDecimal("2000"));

            assertThatThrownBy(() -> account.release(null))
                    .isInstanceOfSatisfying(TradeException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_AMOUNT));
        }
    }
}
