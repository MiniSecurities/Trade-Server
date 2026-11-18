package com.sangyunpark99.tradeserver.order.controller;

import com.sangyunpark99.tradeserver.account.Account;
import com.sangyunpark99.tradeserver.account.repository.AccountRepository;
import com.sangyunpark99.tradeserver.order.Order;
import com.sangyunpark99.tradeserver.order.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
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
    private AccountRepository accountRepository;

    @Autowired
    private OrderRepository orderRepository;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
        accountRepository.deleteAll();
    }

    private Long createAccount(String balance) {
        return accountRepository.save(new Account(new BigDecimal(balance))).getId();
    }

    private ResultActions placeOrder(String body) throws Exception {
        return mockMvc.perform(post("/api/orders")
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
            mockMvc.perform(get("/api/orders"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("없는 경로는 404")
        void unknownPath() throws Exception {
            mockMvc.perform(get("/api/unknown"))
                    .andExpect(status().isNotFound());
        }
    }
}
