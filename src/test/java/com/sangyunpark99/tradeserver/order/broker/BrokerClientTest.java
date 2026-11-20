package com.sangyunpark99.tradeserver.order.broker;

import com.sangyunpark99.tradeserver.broker.BrokerClient;
import com.sangyunpark99.tradeserver.broker.BrokerOrderRequest;
import com.sangyunpark99.tradeserver.broker.BrokerResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class BrokerClientTest {

    private static final String URL = "http://broker/api/orders";

    private MockRestServiceServer server;
    private BrokerClient client;

    private final BrokerOrderRequest request = new BrokerOrderRequest(1L, "AAPL", 10L, new BigDecimal("200"));

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://broker");
        server = MockRestServiceServer.bindTo(builder).build();
        RetryTemplate fastRetry = new RetryTemplate(RetryPolicy.builder()
                .includes(ConnectException.class, HttpConnectTimeoutException.class)
                .maxRetries(3)
                .delay(Duration.ofMillis(1))
                .build());
        client = new BrokerClient(builder.build(), fastRetry);
    }

    @Test
    @DisplayName("주문을 POST /api/orders로 보내고 202면 ACCEPTED")
    void accepted() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"orderId": 1, "symbol": "AAPL", "quantity": 10, "limitPrice": 200}
                        """))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        assertThat(client.sendOrder(request)).isEqualTo(BrokerResult.ACCEPTED);
        server.verify();
    }

    @Test
    @DisplayName("4xx면 재시도 없이 REJECTED")
    void rejectedWithoutRetry() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(client.sendOrder(request)).isEqualTo(BrokerResult.REJECTED);
        server.verify();
    }

    @Test
    @DisplayName("5xx면 받았는지 모르므로 재시도 없이 UNKNOWN")
    void serverErrorIsUnknownWithoutRetry() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(client.sendOrder(request)).isEqualTo(BrokerResult.UNKNOWN);
        server.verify();
    }

    @Test
    @DisplayName("응답 타임아웃이면 받았을 수도 있으므로 재시도 없이 UNKNOWN")
    void readTimeoutIsUnknownWithoutRetry() {
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withException(new HttpTimeoutException("request timed out")));

        assertThat(client.sendOrder(request)).isEqualTo(BrokerResult.UNKNOWN);
        server.verify();
    }

    @Test
    @DisplayName("연결 실패가 계속되면 처음 1번 + 재시도 3번 후 NOT_DELIVERED")
    void connectFailureRetriedThenNotDelivered() {
        server.expect(ExpectedCount.times(4), requestTo(URL))
                .andRespond(withException(new ConnectException()));

        assertThat(client.sendOrder(request)).isEqualTo(BrokerResult.NOT_DELIVERED);
        server.verify();
    }

    @Test
    @DisplayName("연결 타임아웃도 확실히 못 보낸 것이라 재시도 후 NOT_DELIVERED")
    void connectTimeoutRetriedThenNotDelivered() {
        server.expect(ExpectedCount.times(4), requestTo(URL))
                .andRespond(withException(new HttpConnectTimeoutException("connect timed out")));

        assertThat(client.sendOrder(request)).isEqualTo(BrokerResult.NOT_DELIVERED);
        server.verify();
    }

    @Test
    @DisplayName("연결 실패 두 번 뒤 연결되면 ACCEPTED")
    void connectFailureThenRecovered() {
        server.expect(ExpectedCount.times(2), requestTo(URL))
                .andRespond(withException(new ConnectException()));
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        assertThat(client.sendOrder(request)).isEqualTo(BrokerResult.ACCEPTED);
        server.verify();
    }
}
