package com.sangyunpark99.tradeserver.order.entity;

import com.sangyunpark99.tradeserver.common.exception.ErrorCode;
import com.sangyunpark99.tradeserver.common.exception.TradeException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;

    private String symbol;

    private long quantity;

    @Column(precision = 19, scale = 4)
    private BigDecimal limitPrice;

    private long filledQuantity;

    @Column(precision = 19, scale = 4)
    private BigDecimal filledAmount;

    @Enumerated(EnumType.STRING)
    private OrderStatus orderStatus;

    public Order(long accountId, String symbol, long quantity, BigDecimal limitPrice) {
        checkSymbol(symbol);
        checkQuantity(quantity);
        checkLimitPrice(limitPrice);

        this.accountId = accountId;
        this.symbol = symbol;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        this.filledAmount = BigDecimal.ZERO;
        this.orderStatus = OrderStatus.PENDING;
    }

    public void applyFill(long fillQuantity, BigDecimal fillPrice) {
        checkNotRejected();
        checkFillQuantity(fillQuantity);
        checkFillPrice(fillPrice);
        this.filledQuantity += fillQuantity;
        this.filledAmount = this.filledAmount.add(fillPrice.multiply(BigDecimal.valueOf(fillQuantity)));
        this.orderStatus = isCompleted() ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
    }

    public BigDecimal getOrderAmount() {
        return limitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public boolean isCompleted() {
        return quantity == filledQuantity;
    }

    public void markUnknown() {
        if(this.orderStatus == OrderStatus.PENDING || this.orderStatus == OrderStatus.SENDING) {
            this.orderStatus = OrderStatus.UNKNOWN;
        }
    }

    public void reject() {
        if(this.orderStatus == OrderStatus.PENDING || this.orderStatus == OrderStatus.UNKNOWN || this.orderStatus == OrderStatus.SENDING) {
            this.orderStatus = OrderStatus.REJECTED;
            return;
        }

        throw new TradeException(ErrorCode.INVALID_ORDER_STATUS);
    }

    public void accept() {
        if(this.orderStatus == OrderStatus.REJECTED) {
            throw new TradeException(ErrorCode.INVALID_ORDER_STATUS);
        }

        if(this.orderStatus == OrderStatus.PENDING || this.orderStatus == OrderStatus.UNKNOWN || this.orderStatus == OrderStatus.SENDING) {
            this.orderStatus = OrderStatus.ACCEPTED;
        }
    }

    public boolean startSending() {
        if(this.orderStatus == OrderStatus.PENDING) {
            this.orderStatus = OrderStatus.SENDING;
            return true;
        }

        return false;
    }

    public void returnToPending() {
        if(this.orderStatus == OrderStatus.SENDING) {
            this.orderStatus = OrderStatus.PENDING;
        }
    }

    public BigDecimal getRemainingAmount() {
        return getOrderAmount().subtract(filledAmount);
    }

    private void checkSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new TradeException(ErrorCode.INVALID_SYMBOL);
        }
    }

    private void checkQuantity(long quantity) {
        if (quantity <= 0) {
            throw new TradeException(ErrorCode.INVALID_QUANTITY);
        }
    }

    private void checkLimitPrice(BigDecimal limitPrice) {
        if (limitPrice == null || limitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TradeException(ErrorCode.INVALID_LIMIT_PRICE);
        }
    }

    private void checkFillQuantity(long fillQuantity) {
        if (fillQuantity <= 0) {
            throw new TradeException(ErrorCode.INVALID_FILL_QUANTITY);
        }
        if (this.filledQuantity + fillQuantity > this.quantity) {
            throw new TradeException(ErrorCode.OVERFILL);
        }
    }

    private void checkFillPrice(BigDecimal fillPrice) {
        if (fillPrice == null || fillPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TradeException(ErrorCode.INVALID_FILL_PRICE);
        }
        if (fillPrice.compareTo(this.limitPrice) > 0) {
            throw new TradeException(ErrorCode.FILL_PRICE_EXCEEDS_LIMIT);
        }
    }

    private void checkNotRejected() {
        if(this.orderStatus == OrderStatus.REJECTED) {
            throw new TradeException(ErrorCode.FILL_ON_REJECTED_ORDER);
        }
    }
}
