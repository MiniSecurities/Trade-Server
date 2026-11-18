package com.sangyunpark99.tradeserver.order.service;

import com.sangyunpark99.tradeserver.account.Account;
import com.sangyunpark99.tradeserver.account.repository.AccountRepository;
import com.sangyunpark99.tradeserver.common.exception.ErrorCode;
import com.sangyunpark99.tradeserver.common.exception.TradeException;
import com.sangyunpark99.tradeserver.order.Order;
import com.sangyunpark99.tradeserver.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final AccountRepository accountRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public Long placeOrder(long accountId, String symbol, long quantity, BigDecimal limitPrice) {

        Account account = accountRepository.findById(accountId).orElseThrow(() -> new TradeException(ErrorCode.NOT_FOUND_ACCOUNT));
        Order order = new Order(accountId, symbol, quantity, limitPrice);

        BigDecimal orderAmount = order.getOrderAmount();
        account.reserve(orderAmount);

        return orderRepository.save(order).getId();
    }
}
