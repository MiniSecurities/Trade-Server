package com.sangyunpark99.tradeserver.order.controller;

import java.util.UUID;
import com.sangyunpark99.tradeserver.fill.repository.FillRepository;
import com.sangyunpark99.tradeserver.account.Account;
import com.sangyunpark99.tradeserver.auth.jwt.JwtProvider;
import com.sangyunpark99.tradeserver.account.repository.AccountRepository;
import com.sangyunpark99.tradeserver.order.entity.Order;
import com.sangyunpark99.tradeserver.order.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private FillRepository fillRepository;

    @AfterEach
    void tearDown() {
        fillRepository.deleteAll();
        orderRepository.deleteAll();
        accountRepository.deleteAll();
    }

    private Long createAccount(String balance) {
        return accountRepository.save(new Account(new BigDecimal(balance))).getId();
    }

    private String bearer() {
        return "Bearer " + jwtProvider.createAccessToken(1L);
    }

    private ResultActions placeOrder(String body) throws Exception {
        return mockMvc.perform(post("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String body(Object accountId, Object symbol, Object quantity, Object limitPrice) {
        return """
                {"accountId": %s, "symbol": %s, "quantity": %s, "limitPrice": %s}
                """.formatted(accountId, symbol, quantity, limitPrice);
    }

    @Nested
    @DisplayName("주문 접수 성공")
    class Success {

        @Test
        @DisplayName("201과 주문 ID를 돌려주고 주문 금액만큼 돈이 묶인다")
        void returnsCreatedWithOrderId() throws Exception {
            Long accountId = createAccount("10000");

            placeOrder(body(accountId, "\"AAPL\"", 10, 200))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.orderId").value(notNullValue()));

            Account account = accountRepository.findById(accountId).orElseThrow();
            assertThat(account.getReservedBalance()).isEqualByComparingTo("2000");

            Order order = orderRepository.findAll().getFirst();
            assertThat(order.getAccountId()).isEqualTo(accountId);
            assertThat(order.getQuantity()).isEqualTo(10);
            assertThat(order.getLimitPrice()).isEqualByComparingTo("200");
        }
    }

    @Nested
    @DisplayName("요청 검증 실패는 400 INVALID_REQUEST")
    class InvalidRequest {

        @Test
        @DisplayName("계좌 ID가 없으면 400")
        void missingAccountId() throws Exception {
            placeOrder("""
                    {"symbol": "AAPL", "quantity": 10, "limitPrice": 200}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.message").value(containsString("accountId")));
        }

        @Test
        @DisplayName("종목이 공백이면 400")
        void blankSymbol() throws Exception {
            Long accountId = createAccount("10000");

            placeOrder(body(accountId, "\" \"", 10, 200))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.message").value(containsString("symbol")));
        }

        @Test
        @DisplayName("수량이 0이면 400")
        void zeroQuantity() throws Exception {
            Long accountId = createAccount("10000");

            placeOrder(body(accountId, "\"AAPL\"", 0, 200))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.message").value(containsString("quantity")));
        }

        @Test
        @DisplayName("지정가가 음수면 400")
        void negativeLimitPrice() throws Exception {
            Long accountId = createAccount("10000");

            placeOrder(body(accountId, "\"AAPL\"", 10, -1))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.message").value(containsString("limitPrice")));
        }

        @Test
        @DisplayName("숫자 자리에 문자열이 오면 400")
        void wrongType() throws Exception {
            placeOrder(body("\"abc\"", "\"AAPL\"", 10, 200))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("JSON 형식이 깨지면 400")
        void malformedJson() throws Exception {
            placeOrder("{\"accountId\": 1,")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }

        @Test
        @DisplayName("검증에 실패하면 돈이 묶이지 않고 주문도 남지 않는다")
        void nothingChangesOnInvalidRequest() throws Exception {
            Long accountId = createAccount("10000");

            placeOrder(body(accountId, "\"AAPL\"", 0, 200))
                    .andExpect(status().isBadRequest());

            assertThat(accountRepository.findById(accountId).orElseThrow().getReservedBalance())
                    .isEqualByComparingTo("0");
            assertThat(orderRepository.count()).isZero();
        }
    }

    @Nested
    @DisplayName("도메인 거절은 ErrorCode의 상태로 응답")
    class DomainRejection {

        @Test
        @DisplayName("주문 가능 금액이 부족하면 409")
        void insufficientAvailableBalance() throws Exception {
            Long accountId = createAccount("1999");

            placeOrder(body(accountId, "\"AAPL\"", 10, 200))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INSUFFICIENT_AVAILABLE_BALANCE"))
                    .andExpect(jsonPath("$.message").value("주문 가능 금액이 부족합니다."));

            assertThat(orderRepository.count()).isZero();
        }

        @Test
        @DisplayName("없는 계좌면 404")
        void accountNotFound() throws Exception {
            placeOrder(body(999_999, "\"AAPL\"", 10, 200))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND_ACCOUNT"));
        }
    }

    @Nested
    @DisplayName("Spring이 만든 4xx는 500으로 바뀌지 않는다")
    class FrameworkErrors {

        @Test
        @DisplayName("지원하지 않는 메서드는 405")
        void methodNotAllowed() throws Exception {
            mockMvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, bearer()))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("없는 경로는 404")
        void unknownPath() throws Exception {
            mockMvc.perform(get("/api/unknown").header(HttpHeaders.AUTHORIZATION, bearer()))
                    .andExpect(status().isNotFound());
        }
    }

    private Long placeOrderAndGetId(Long accountId, int quantity, int limitPrice) throws Exception {
        placeOrder(body(accountId, "\"AAPL\"", quantity, limitPrice))
                .andExpect(status().isCreated());
        return orderRepository.findAll().getLast().getId();
    }

    private ResultActions applyFill(String body) throws Exception {
        return mockMvc.perform(post("/api/fills")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String fillBody(Object orderId, Object fillQuantity, Object fillPrice) {
        return fillBody(UUID.randomUUID().toString(), orderId, fillQuantity, fillPrice);
    }

    private String fillBody(String fillId, Object orderId, Object fillQuantity, Object fillPrice) {
        return """
                {"fillId": "%s", "orderId": %s, "fillQuantity": %s, "fillPrice": %s}
                """.formatted(fillId, orderId, fillQuantity, fillPrice);
    }

    @Nested
    @DisplayName("체결 통보 받기")
    class ApplyFill {

        @Test
        @DisplayName("체결을 반영하고 204를 돌려준다")
        void appliesFillAndReturnsNoContent() throws Exception {
            Long accountId = createAccount("10000");
            Long orderId = placeOrderAndGetId(accountId, 10, 200);

            applyFill(fillBody(orderId, 3, "197.42"))
                    .andExpect(status().isNoContent());

            Account account = accountRepository.findById(accountId).orElseThrow();
            assertThat(account.getBalance()).isEqualByComparingTo("9407.74");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("1407.74");

            Order order = orderRepository.findById(orderId).orElseThrow();
            assertThat(order.getFilledQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("부분 체결 세 번으로 완료되면 남은 돈이 풀린다")
        void completesWithThreePartialFills() throws Exception {
            Long accountId = createAccount("10000");
            Long orderId = placeOrderAndGetId(accountId, 10, 200);

            applyFill(fillBody(orderId, 3, "197.42")).andExpect(status().isNoContent());
            applyFill(fillBody(orderId, 5, "199.81")).andExpect(status().isNoContent());
            applyFill(fillBody(orderId, 2, "196.10")).andExpect(status().isNoContent());

            Account account = accountRepository.findById(accountId).orElseThrow();
            assertThat(account.getBalance()).isEqualByComparingTo("8016.49");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("0");
            assertThat(orderRepository.findById(orderId).orElseThrow().isCompleted()).isTrue();
        }

        @Test
        @DisplayName("같은 체결 ID가 두 번 오면 둘 다 204를 돌려주고 한 번만 반영한다")
        void duplicateFillIdReturnsNoContentAndAppliesOnce() throws Exception {
            Long accountId = createAccount("10000");
            Long orderId = placeOrderAndGetId(accountId, 10, 200);

            applyFill(fillBody("fill-1", orderId, 3, "197.42")).andExpect(status().isNoContent());
            applyFill(fillBody("fill-1", orderId, 3, "197.42")).andExpect(status().isNoContent());

            Account account = accountRepository.findById(accountId).orElseThrow();
            assertThat(account.getBalance()).isEqualByComparingTo("9407.74");
            assertThat(orderRepository.findById(orderId).orElseThrow().getFilledQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("체결 ID가 없으면 400")
        void missingFillId() throws Exception {
            applyFill("""
                    {"orderId": 1, "fillQuantity": 3, "fillPrice": 197.42}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.message").value(containsString("fillId")));
        }

        @Test
        @DisplayName("체결가가 없으면 400")
        void missingFillPrice() throws Exception {
            applyFill("""
                    {"fillId": "fill-1", "orderId": 1, "fillQuantity": 3}
                    """)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.message").value(containsString("fillPrice")));
        }

        @Test
        @DisplayName("모르는 주문의 체결이면 500 FILL_ORDER_NOT_FOUND")
        void unknownOrder() throws Exception {
            applyFill(fillBody(999_999, 3, "197.42"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("FILL_ORDER_NOT_FOUND"));
        }

        @Test
        @DisplayName("체결가가 지정가보다 높으면 500이고 주문과 계좌가 바뀌지 않는다")
        void fillPriceAboveLimit() throws Exception {
            Long accountId = createAccount("10000");
            Long orderId = placeOrderAndGetId(accountId, 10, 200);

            applyFill(fillBody(orderId, 3, "200.01"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("FILL_PRICE_EXCEEDS_LIMIT"));

            Account account = accountRepository.findById(accountId).orElseThrow();
            assertThat(account.getBalance()).isEqualByComparingTo("10000");
            assertThat(account.getReservedBalance()).isEqualByComparingTo("2000");
            assertThat(orderRepository.findById(orderId).orElseThrow().getFilledQuantity()).isZero();
        }

        @Test
        @DisplayName("주문 수량을 넘는 체결이면 500 OVERFILL")
        void overfill() throws Exception {
            Long accountId = createAccount("10000");
            Long orderId = placeOrderAndGetId(accountId, 10, 200);

            applyFill(fillBody(orderId, 11, "199"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("OVERFILL"));
        }
    }

    @Nested
    @DisplayName("주문 상세 조회")
    class GetOrder {

        @Test
        @DisplayName("주문 정보, 평균 체결가, 체결 내역을 체결 순서대로 돌려준다")
        void returnsOrderWithFills() throws Exception {
            Long accountId = createAccount("10000");
            Long orderId = placeOrderAndGetId(accountId, 10, 200);
            applyFill(fillBody("fill-1", orderId, 3, "197.42")).andExpect(status().isNoContent());
            applyFill(fillBody("fill-2", orderId, 2, "199")).andExpect(status().isNoContent());

            mockMvc.perform(get("/api/orders/{orderId}", orderId).header(HttpHeaders.AUTHORIZATION, bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value(orderId))
                    .andExpect(jsonPath("$.symbol").value("AAPL"))
                    .andExpect(jsonPath("$.quantity").value(10))
                    .andExpect(jsonPath("$.status").value("PARTIALLY_FILLED"))
                    .andExpect(jsonPath("$.filledQuantity").value(5))
                    .andExpect(jsonPath("$.averagePrice").value(198.052))
                    .andExpect(jsonPath("$.fills.length()").value(2))
                    .andExpect(jsonPath("$.fills[0].fillId").value("fill-1"))
                    .andExpect(jsonPath("$.fills[0].quantity").value(3))
                    .andExpect(jsonPath("$.fills[1].fillId").value("fill-2"))
                    .andExpect(jsonPath("$.fills[1].filledAt").value(notNullValue()));
        }

        @Test
        @DisplayName("체결이 없으면 평균 체결가는 null이고 체결 내역은 비어 있다")
        void noFills() throws Exception {
            Long accountId = createAccount("10000");
            Long orderId = placeOrderAndGetId(accountId, 10, 200);

            mockMvc.perform(get("/api/orders/{orderId}", orderId).header(HttpHeaders.AUTHORIZATION, bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.filledQuantity").value(0))
                    .andExpect(jsonPath("$.averagePrice").value(nullValue()))
                    .andExpect(jsonPath("$.fills").isEmpty());
        }

        @Test
        @DisplayName("없는 주문이면 404")
        void notFound() throws Exception {
            mockMvc.perform(get("/api/orders/{orderId}", 999_999).header(HttpHeaders.AUTHORIZATION, bearer()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND_ORDER"));
        }
    }
}
