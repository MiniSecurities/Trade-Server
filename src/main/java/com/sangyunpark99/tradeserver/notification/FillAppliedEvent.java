package com.sangyunpark99.tradeserver.notification;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FillAppliedEvent(long userId, String fillId, long orderId, String symbol, long quantity,
                               BigDecimal price, LocalDateTime filledAt) {
}
