package com.sangyunpark99.tradeserver.quote.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Quote(long sequence, String symbol, BigDecimal price, LocalDateTime quotedAt) {
}
