package com.sangyunpark99.tradeserver.order.broker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class BrokerClientTest {

    private MockRestServiceServer server;
    private BrokerClient client;

    private final BrokerOrderRequest request = new BrokerOrderRequest(1L, "AAPL", 10L, new BigDecimal("200"));

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://broker");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new BrokerClient(builder.build());
    }

    @Test
    @DisplayName("주문을 POST /api/orders로 보내고 202면 ACCEPTED")
    void accepted() {
        server.expect(requestTo("http://broker/api/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"orderId": 1, "symbol": "AAPL", "quantity": 10, "limitPrice": 200}
                        """))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        assertThat(client.sendOrder(request)).isEqualTo(BrokerResult.ACCEPTED);
        server.verify();
    }

    @Test
    @DisplayName("4xx면 REJECTED")
    void rejected() {
        server.expect(requestTo("http://broker/api/orders"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(client.sendOrder(request)).isEqualTo(BrokerResult.REJECTED);
    }

    @Test
    @DisplayName("5xx면 받았는지 모르므로 UNKNOWN")
    void serverErrorIsUnknown() {
        server.expect(requestTo("http://broker/api/orders"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(client.sendOrder(request)).isEqualTo(BrokerResult.UNKNOWN);
    }

    @Test
    @DisplayName("타임아웃이면 UNKNOWN")
    void timeoutIsUnknown() {
        server.expect(requestTo("http://broker/api/orders"))
                .andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThat(client.sendOrder(request)).isEqualTo(BrokerResult.UNKNOWN);
    }
}
