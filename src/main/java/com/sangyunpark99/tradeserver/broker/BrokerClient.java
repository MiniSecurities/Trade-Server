package com.sangyunpark99.tradeserver.broker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrokerClient {

    private final RestClient brokerRestClient;
    private final RetryTemplate brokerRetryTemplate;

    public BrokerResult sendOrder(BrokerOrderRequest request) {
        try {

            brokerRetryTemplate.execute(() -> {
                brokerRestClient.post()
                        .uri("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .toBodilessEntity();
                return null;
            });

            return BrokerResult.ACCEPTED;
        } catch (RetryException e) {
            Throwable cause = e.getCause();

            if(cause instanceof HttpClientErrorException clientErrorException) {
                log.warn("브로커 서버가 주문을 거절함 orderId={} status={}", request.orderId(), clientErrorException.getStatusCode());
                return BrokerResult.REJECTED;
            }

            if(isNotDelivered(cause)) {
                log.error("브로커 서버에 연결하지 못함 재시도 {}회 orderId={}", e.getRetryCount(), request.orderId());
                return BrokerResult.NOT_DELIVERED;
            }

            log.error("브로커 서버 주문 결과 모름 orderId={}", request.orderId(), cause);
            return BrokerResult.UNKNOWN;
        }
    }

    private boolean isNotDelivered(Throwable failure) {
        Throwable cause = failure.getCause();
        return cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException;
    }
}
