package com.sangyunpark99.tradeserver.order.facade;

import com.sangyunpark99.tradeserver.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Retryable(includes = OptimisticLockingFailureException.class, maxRetries = 3, delay = 10)
public class OrderFacade {

    private final OrderService orderService;

    public Long placeOrder(long userId, long accountId, String symbol, long quantity, BigDecimal limitPrice) {
        return orderService.placeOrder(userId, accountId, symbol, quantity, limitPrice);
    }

    public void applyFill(String fillId, long orderId, long fillQuantity, BigDecimal fillPrice){
        orderService.applyFill(fillId, orderId, fillQuantity, fillPrice);
    }

    public void reject(long orderId) {
        orderService.reject(orderId);
    }
}
