package com.sangyunpark99.tradeserver.order.controller.dto;

import com.sangyunpark99.tradeserver.fill.Fill;
import com.sangyunpark99.tradeserver.order.entity.Order;
import com.sangyunpark99.tradeserver.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

public record OrderDetailResponse(
        Long orderId,
        String symbol,
        long quantity,
        BigDecimal limitPrice,
        OrderStatus status,
        long filledQuantity,
        BigDecimal filledAmount,
        BigDecimal averagePrice,
        List<FillResponse> fills
) {

    public static OrderDetailResponse of(Order order, List<Fill> fills) {
        return new OrderDetailResponse(
                order.getId(),
                order.getSymbol(),
                order.getQuantity(),
                order.getLimitPrice(),
                order.getOrderStatus(),
                order.getFilledQuantity(),
                order.getFilledAmount(),
                order.getAveragePrice(),
                fills.stream().map(FillResponse::from).toList()
        );
    }
}
