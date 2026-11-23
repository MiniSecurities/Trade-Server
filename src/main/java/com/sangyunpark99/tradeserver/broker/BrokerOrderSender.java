package com.sangyunpark99.tradeserver.broker;

import com.sangyunpark99.tradeserver.order.entity.Order;
import com.sangyunpark99.tradeserver.order.facade.OrderFacade;
import com.sangyunpark99.tradeserver.order.repository.OrderRepository;
import com.sangyunpark99.tradeserver.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrokerOrderSender {

    private final OrderRepository orderRepository;
    private final BrokerClient brokerClient;
    private final OrderFacade orderFacade;
    private final OrderService orderService;

    public void send(Long orderId) {
        try {
            Order order = orderRepository.findById(orderId).orElseThrow();

            boolean claimed = orderService.startSending(orderId);

            if(!claimed) {
                log.info("이미 다른 곳에서 처리중 입니다. orderId={}", orderId);
                return;
            }

            BrokerResult result = brokerClient.sendOrder(new BrokerOrderRequest(
                    order.getId(), order.getSymbol(), order.getQuantity(), order.getLimitPrice()));

            switch (result) {
                case ACCEPTED -> orderService.markAccepted(orderId);
                case UNKNOWN -> orderService.markUnknown(orderId);
                case REJECTED -> orderFacade.reject(orderId);
                case NOT_DELIVERED -> orderService.returnToPending(orderId);
            }
        } catch (RuntimeException e) {
            log.error("브로커 주문 전달 처리 실패 orderId={}", orderId, e);
        }
    }
}
