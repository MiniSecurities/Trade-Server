package com.sangyunpark99.tradeserver.auth.controller;

import com.sangyunpark99.tradeserver.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    private ResultActions signUp(String body) throws Exception {
        return mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String body(String email, String name, String password) {
        return """
                {"email": "%s", "name": "%s", "password": "%s"}
                """.formatted(email, name, password);
    }

    @Test
    @DisplayName("가입하면 201과 사용자 ID를 돌려주고 비밀번호는 응답에 없다")
    void signUp() throws Exception {
        signUp(body("user@toss.im", "박상윤", "password123"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(notNullValue()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 409")
    void duplicateEmail() throws Exception {
        signUp(body("user@toss.im", "박상윤", "password123")).andExpect(status().isCreated());

        signUp(body("user@toss.im", "다른 사람", "other-password"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("이메일 형식이 아니면 400")
    void invalidEmailFormat() throws Exception {
        signUp(body("not-an-email", "박상윤", "password123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("email")));
    }

    @Test
    @DisplayName("비밀번호가 8자보다 짧으면 400")
    void shortPassword() throws Exception {
        signUp(body("user@toss.im", "박상윤", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("password")));
    }

    @Test
    @DisplayName("이름이 비어 있으면 400")
    void blankName() throws Exception {
        signUp(body("user@toss.im", " ", "password123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(containsString("name")));
    }
}
