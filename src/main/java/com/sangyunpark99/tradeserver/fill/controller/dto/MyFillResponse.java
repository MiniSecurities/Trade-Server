package com.sangyunpark99.tradeserver.fill.controller.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MyFillResponse(
        String fillId,
        Long orderId,
        String symbol,
        long quantity,
        BigDecimal price,
        LocalDateTime filledAt
) {
}
