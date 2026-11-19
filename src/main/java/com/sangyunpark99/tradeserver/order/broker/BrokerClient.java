package com.sangyunpark99.tradeserver.order.broker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrokerClient {

    private final RestClient brokerRestClient;

    public BrokerResult sendOrder(BrokerOrderRequest request) {
        try {
            brokerRestClient.post()
                    .uri("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            return BrokerResult.ACCEPTED;
        } catch (HttpClientErrorException e) {
            log.warn("브로커가 주문을 거절함 orderId={} status={}", request.orderId(), e.getStatusCode());
            return BrokerResult.REJECTED;
        } catch (RestClientException e) {
            log.error("브로커 주문 결과 모름 orderId={}", request.orderId(), e);
            return BrokerResult.UNKNOWN;
        }
    }
}
