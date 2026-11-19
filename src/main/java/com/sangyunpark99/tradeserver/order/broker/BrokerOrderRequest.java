package com.sangyunpark99.tradeserver.order.broker;

import java.math.BigDecimal;

public record BrokerOrderRequest(Long orderId, String symbol, Long quantity, BigDecimal limitPrice) {
}
