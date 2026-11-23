package com.sangyunpark99.tradeserver.order.service;

import com.sangyunpark99.tradeserver.account.Account;
import com.sangyunpark99.tradeserver.account.repository.AccountRepository;
import com.sangyunpark99.tradeserver.common.exception.ErrorCode;
import com.sangyunpark99.tradeserver.common.exception.TradeException;
import com.sangyunpark99.tradeserver.broker.OrderPlacedEvent;
import com.sangyunpark99.tradeserver.fill.Fill;
import com.sangyunpark99.tradeserver.fill.repository.FillRepository;
import com.sangyunpark99.tradeserver.order.entity.Order;
import com.sangyunpark99.tradeserver.order.controller.dto.OrderDetailResponse;
import com.sangyunpark99.tradeserver.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final AccountRepository accountRepository;
    private final OrderRepository orderRepository;
    private final FillRepository fillRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public Long placeOrder(long accountId, String symbol, long quantity, BigDecimal limitPrice) {

        Account account = accountRepository.findById(accountId).orElseThrow(() -> new TradeException(ErrorCode.NOT_FOUND_ACCOUNT));
        Order order = new Order(accountId, symbol, quantity, limitPrice);

        BigDecimal orderAmount = order.getOrderAmount();
        account.reserve(orderAmount);

        Long orderId = orderRepository.save(order).getId();
        applicationEventPublisher.publishEvent(new OrderPlacedEvent(orderId));

        return orderId;
    }

    @Transactional
    public void applyFill(String fillId, long orderId, long fillQuantity, BigDecimal fillPrice) {

        Order order = orderRepository.findWithLockById(orderId).orElseThrow(() -> new TradeException(ErrorCode.FILL_ORDER_NOT_FOUND));

        if(fillRepository.existsByFillId(fillId)) {
            log.info("중복 체결 통보 fillId={} orderId={}", fillId, orderId);
            return;
        }

        Fill fill = new Fill(orderId, fillPrice, fillQuantity, fillId);
        fillRepository.save(fill);

        Account account = accountRepository.findById(order.getAccountId()).orElseThrow(() -> new TradeException(ErrorCode.FILL_ACCOUNT_NOT_FOUND));

        order.applyFill(fillQuantity, fillPrice);
        account.applyFill(fillPrice.multiply(BigDecimal.valueOf(fillQuantity)));

        if(order.isCompleted() && order.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0) {
            account.release(order.getRemainingAmount());
        }
    }

    @Transactional
    public void reject(long orderId) {
        Order order = orderRepository.findWithLockById(orderId).orElseThrow(() -> new TradeException(ErrorCode.FILL_ORDER_NOT_FOUND));
        Account account = accountRepository.findById(order.getAccountId()).orElseThrow(() -> new TradeException(ErrorCode.FILL_ACCOUNT_NOT_FOUND));

        order.reject();
        account.release(order.getRemainingAmount());
    }

    @Transactional
    public void markAccepted(long orderId) {
        Order order = orderRepository.findWithLockById(orderId).orElseThrow(() -> new TradeException(ErrorCode.FILL_ORDER_NOT_FOUND));
        order.accept();
    }

    @Transactional
    public void markUnknown(long orderId) {
        Order order = orderRepository.findWithLockById(orderId).orElseThrow(() -> new TradeException(ErrorCode.FILL_ORDER_NOT_FOUND));
        order.markUnknown();
    }

    @Transactional
    public boolean startSending(long orderId) {
        Order order = orderRepository.findWithLockById(orderId).orElseThrow(() -> new TradeException(ErrorCode.NOT_FOUND_ORDER));
        return order.startSending();
    }

    @Transactional
    public void returnToPending(Long orderId) {
        Order order = orderRepository.findWithLockById(orderId).orElseThrow(() -> new TradeException(ErrorCode.NOT_FOUND_ORDER));
        order.returnToPending();
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getOrder(long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new TradeException(ErrorCode.NOT_FOUND_ORDER));
        List<Fill> fills = fillRepository.findByOrderIdOrderByIdAsc(orderId);
        return OrderDetailResponse.of(order, fills);
    }
}
