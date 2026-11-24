package com.sangyunpark99.tradeserver.notification;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FillNotification(
        String fillId,
        long orderId,
        String symbol,
        long quantity,
        BigDecimal price,
        LocalDateTime filledAt
) {

    public static FillNotification from(FillAppliedEvent event) {
        return new FillNotification(
                event.fillId(),
                event.orderId(),
                event.symbol(),
                event.quantity(),
                event.price(),
                event.filledAt()
        );
    }
}