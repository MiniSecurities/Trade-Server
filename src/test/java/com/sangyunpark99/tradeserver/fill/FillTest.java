package com.sangyunpark99.tradeserver.fill;

import com.sangyunpark99.tradeserver.common.exception.ErrorCode;
import com.sangyunpark99.tradeserver.common.exception.TradeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FillTest {

    private static final BigDecimal PRICE = new BigDecimal("100000");

    private static void assertError(Executable executable, ErrorCode errorCode) {
        assertThatThrownBy(executable::execute)
                .isInstanceOfSatisfying(TradeException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }

    @Test
    @DisplayName("체결 내역은 주문 ID, 체결가, 수량, 체결 ID를 가지고 만들어진 시각이 기록된다")
    void create() {
        LocalDateTime before = LocalDateTime.now();

        Fill fill = new Fill(1L, PRICE, 3, "fill-1");

        assertThat(fill.getOrderId()).isEqualTo(1L);
        assertThat(fill.getPrice()).isEqualByComparingTo("100000");
        assertThat(fill.getQuantity()).isEqualTo(3);
        assertThat(fill.getFillId()).isEqualTo("fill-1");
        assertThat(fill.getCreatedAt()).isNotNull().isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("주문 ID가 없으면 만들 수 없다")
    void rejectNullOrderId() {
        assertError(() -> new Fill(null, PRICE, 3, "fill-1"), ErrorCode.FILL_ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("체결가가 null이거나 0 이하면 만들 수 없다")
    void rejectInvalidPrice() {
        assertError(() -> new Fill(1L, null, 3, "fill-1"), ErrorCode.INVALID_FILL_PRICE);
        assertError(() -> new Fill(1L, BigDecimal.ZERO, 3, "fill-1"), ErrorCode.INVALID_FILL_PRICE);
        assertError(() -> new Fill(1L, new BigDecimal("-1"), 3, "fill-1"), ErrorCode.INVALID_FILL_PRICE);
    }

    @Test
    @DisplayName("체결 수량이 0 이하면 만들 수 없다")
    void rejectInvalidQuantity() {
        assertError(() -> new Fill(1L, PRICE, 0, "fill-1"), ErrorCode.INVALID_FILL_QUANTITY);
        assertError(() -> new Fill(1L, PRICE, -1, "fill-1"), ErrorCode.INVALID_FILL_QUANTITY);
    }

    @Test
    @DisplayName("체결 ID가 null이거나 비어 있으면 만들 수 없다")
    void rejectBlankFillId() {
        assertError(() -> new Fill(1L, PRICE, 3, null), ErrorCode.INVALID_FILL_ID);
        assertError(() -> new Fill(1L, PRICE, 3, ""), ErrorCode.INVALID_FILL_ID);
        assertError(() -> new Fill(1L, PRICE, 3, "  "), ErrorCode.INVALID_FILL_ID);
    }
}
