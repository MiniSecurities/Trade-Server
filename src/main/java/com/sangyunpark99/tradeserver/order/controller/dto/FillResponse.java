package com.sangyunpark99.tradeserver.order.controller.dto;

import com.sangyunpark99.tradeserver.fill.Fill;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FillResponse(String fillId, long quantity, BigDecimal price, LocalDateTime filledAt) {

    public static FillResponse from(Fill fill) {
        return new FillResponse(fill.getFillId(), fill.getQuantity(), fill.getPrice(), fill.getCreatedAt());
    }
}
