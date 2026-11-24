package com.sangyunpark99.tradeserver.notification;

import com.sangyunpark99.tradeserver.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class FillNotificationListener {

    private final NotificationService notificationService;

    @TransactionalEventListener
    public void onFillApplied(FillAppliedEvent event){
        notificationService.sendFill(event);
    }
}
