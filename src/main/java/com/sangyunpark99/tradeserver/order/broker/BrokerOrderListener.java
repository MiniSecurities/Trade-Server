package com.sangyunpark99.tradeserver.order.broker;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "broker.order-sending.enabled", havingValue = "true")
public class BrokerOrderListener {

    private final TaskExecutor brokerExecutor;
    private final BrokerOrderSender brokerOrderSender;

    @TransactionalEventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        brokerExecutor.execute(() -> brokerOrderSender.send(event.orderId()));
    }
}
