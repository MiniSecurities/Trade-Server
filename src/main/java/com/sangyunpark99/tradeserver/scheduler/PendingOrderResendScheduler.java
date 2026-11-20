package com.sangyunpark99.tradeserver.scheduler;

import com.sangyunpark99.tradeserver.broker.BrokerOrderSender;
import com.sangyunpark99.tradeserver.order.entity.Order;
import com.sangyunpark99.tradeserver.order.entity.OrderStatus;
import com.sangyunpark99.tradeserver.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PendingOrderResendScheduler {

    private final OrderRepository orderRepository;
    private final BrokerOrderSender brokerOrderSender;

    @Scheduled(fixedDelay = 10_000)
    public void resendPendingOrders() {
        List<Order> orders = orderRepository.findTop50ByOrderStatusOrderById(OrderStatus.PENDING);

        for(int i = 0; i < orders.size(); i++) {
            brokerOrderSender.send(orders.get(i).getId());
        }
    }
}
