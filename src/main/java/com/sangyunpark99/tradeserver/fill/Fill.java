package com.sangyunpark99.tradeserver.fill;

import com.sangyunpark99.tradeserver.common.exception.ErrorCode;
import com.sangyunpark99.tradeserver.common.exception.TradeException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Fill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;

    @Column(precision = 19, scale = 4)
    private BigDecimal price;

    private long quantity;

    @Column(nullable = false, unique = true)
    private String fillId;

    private LocalDateTime createdAt;

    public Fill(Long orderId, BigDecimal price, long quantity, String fillId) {
        checkOrderId(orderId);
        checkPrice(price);
        checkQuantity(quantity);
        checkFillId(fillId);
        this.orderId = orderId;
        this.price = price;
        this.quantity = quantity;
        this.fillId = fillId;
        this.createdAt = LocalDateTime.now();
    }

    private void checkOrderId(Long orderId) {
        if (orderId == null) {
            throw new TradeException(ErrorCode.FILL_ORDER_NOT_FOUND);
        }
    }

    private void checkPrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TradeException(ErrorCode.INVALID_FILL_PRICE);
        }
    }

    private void checkQuantity(long quantity) {
        if (quantity <= 0) {
            throw new TradeException(ErrorCode.INVALID_FILL_QUANTITY);
        }
    }

    private void checkFillId(String fillId) {
        if (fillId == null || fillId.isBlank()) {
            throw new TradeException(ErrorCode.INVALID_FILL_ID);
        }
    }
}
