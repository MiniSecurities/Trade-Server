package com.sangyunpark99.tradeserver.quote.service;

import com.sangyunpark99.tradeserver.quote.dto.Quote;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QuotePublisher {

    private static final String QUOTE_TOPIC_PREFIX = "/topic/quotes/";

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(Quote quote) {
        messagingTemplate.convertAndSend(QUOTE_TOPIC_PREFIX + quote.symbol(), quote);
    }
}
