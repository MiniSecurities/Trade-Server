package com.sangyunpark99.tradeserver.account.controller;

import com.sangyunpark99.tradeserver.account.Account;
import com.sangyunpark99.tradeserver.account.repository.AccountRepository;
import com.sangyunpark99.tradeserver.auth.jwt.JwtProvider;
import com.sangyunpark99.tradeserver.user.User;
import com.sangyunpark99.tradeserver.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String bearer(long userId) {
        return "Bearer " + jwtProvider.createAccessToken(userId);
    }

    @Test
    @DisplayName("계좌를 열면 201과 계좌 ID를 돌려주고, 토큰의 사용자가 주인인 0원 계좌가 만들어진다")
    void openAccount() throws Exception {
        long userId = userRepository.save(new User("user@toss.im", "박상윤", "$2a$10$hash")).getId();

        mockMvc.perform(post("/api/accounts").header(HttpHeaders.AUTHORIZATION, bearer(userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").isNumber());

        Account account = accountRepository.findAll().getFirst();
        assertThat(account.isOwnedBy(userId)).isTrue();
        assertThat(account.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("토큰이 없으면 401")
    void requiresToken() throws Exception {
        mockMvc.perform(post("/api/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertThat(accountRepository.count()).isZero();
    }

    @Test
    @DisplayName("토큰은 유효하지만 사용자가 없으면 401")
    void unknownUser() throws Exception {
        mockMvc.perform(post("/api/accounts").header(HttpHeaders.AUTHORIZATION, bearer(999_999L)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertThat(accountRepository.count()).isZero();
    }

    private long createUser() {
        return userRepository.save(new User("user@toss.im", "박상윤", "$2a$10$hash")).getId();
    }

    private ResultActions deposit(long userId, String amountJson) throws Exception {
        return mockMvc.perform(post("/api/accounts/deposit")
                .header(HttpHeaders.AUTHORIZATION, bearer(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount": %s}
                        """.formatted(amountJson)));
    }

    @Test
    @DisplayName("입금하면 200과 입금 후 잔고, 주문 가능 금액을 돌려준다")
    void depositReturnsBalance() throws Exception {
        long userId = createUser();
        mockMvc.perform(post("/api/accounts").header(HttpHeaders.AUTHORIZATION, bearer(userId)))
                .andExpect(status().isCreated());

        deposit(userId, "1000000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1000000.0))
                .andExpect(jsonPath("$.availableBalance").value(1000000.0));

        deposit(userId, "500000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(1500000.0));
    }

    @Test
    @DisplayName("입금액이 없거나 0 이하면 400")
    void depositInvalidAmount() throws Exception {
        long userId = createUser();
        mockMvc.perform(post("/api/accounts").header(HttpHeaders.AUTHORIZATION, bearer(userId)))
                .andExpect(status().isCreated());

        for (String amount : new String[]{"null", "0", "-1000"}) {
            deposit(userId, amount)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        assertThat(accountRepository.findAll().getFirst().getBalance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("계좌가 없는 사용자가 입금하면 404")
    void depositWithoutAccount() throws Exception {
        long userId = createUser();

        deposit(userId, "1000")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND_ACCOUNT"));
    }

    @Test
    @DisplayName("토큰 없이 입금하면 401")
    void depositRequiresToken() throws Exception {
        mockMvc.perform(post("/api/accounts/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1000}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
