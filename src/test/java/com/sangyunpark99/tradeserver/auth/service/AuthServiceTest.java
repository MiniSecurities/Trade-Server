package com.sangyunpark99.tradeserver.auth.service;

import com.sangyunpark99.tradeserver.auth.JwtProvider;
import com.sangyunpark99.tradeserver.auth.controller.dto.LoginResponse;
import com.sangyunpark99.tradeserver.common.exception.ErrorCode;
import com.sangyunpark99.tradeserver.common.exception.TradeException;
import com.sangyunpark99.tradeserver.user.User;
import com.sangyunpark99.tradeserver.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AuthServiceTest {

    private static final String EMAIL = "user@toss.im";
    private static final String NAME = "박상윤";
    private static final String PASSWORD = "password123";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProvider jwtProvider;

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    private void assertTradeException(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(TradeException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }

    @Test
    @DisplayName("가입하면 사용자가 저장되고 비밀번호는 원문이 아닌 해시로 저장된다")
    void signUpSavesHashedPassword() {
        long userId = authService.signUp(EMAIL, NAME, PASSWORD);

        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getEmail()).isEqualTo(EMAIL);
        assertThat(user.getName()).isEqualTo(NAME);
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("같은 비밀번호로 가입해도 사용자마다 해시가 다르다")
    void sameRawPasswordHasDifferentHashes() {
        long first = authService.signUp("a@toss.im", NAME, PASSWORD);
        long second = authService.signUp("b@toss.im", NAME, PASSWORD);

        String firstHash = userRepository.findById(first).orElseThrow().getPasswordHash();
        String secondHash = userRepository.findById(second).orElseThrow().getPasswordHash();
        assertThat(firstHash).isNotEqualTo(secondHash);
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 DUPLICATE_EMAIL")
    void duplicateEmail() {
        authService.signUp(EMAIL, NAME, PASSWORD);

        assertTradeException(() -> authService.signUp(EMAIL, "다른 사람", "other-password"), ErrorCode.DUPLICATE_EMAIL);
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 이메일로 동시에 가입해도 한 명만 가입되고 나머지는 DUPLICATE_EMAIL")
    void concurrentSignUpWithSameEmail() throws Exception {
        int threadCount = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threadCount; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return authService.signUp(EMAIL, NAME, PASSWORD);
                }));
            }
            ready.await();
            start.countDown();

            int succeeded = 0;
            int duplicated = 0;
            for (Future<Long> result : results) {
                try {
                    result.get(10, TimeUnit.SECONDS);
                    succeeded++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause())
                            .isInstanceOfSatisfying(TradeException.class,
                                    t -> assertThat(t.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_EMAIL));
                    duplicated++;
                }
            }

            assertThat(succeeded).isEqualTo(1);
            assertThat(duplicated).isEqualTo(threadCount - 1);
            assertThat(userRepository.count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("이메일과 비밀번호가 맞으면 사용자 ID가 담긴 토큰과 만료 시간(초)을 돌려준다")
    void loginReturnsAccessToken() {
        long userId = authService.signUp(EMAIL, NAME, PASSWORD);

        LoginResponse response = authService.login(EMAIL, PASSWORD);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(jwtProvider.getUserId(response.accessToken())).isEqualTo(userId);
        assertThat(response.expiresIn()).isEqualTo(1800);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 LOGIN_FAILED")
    void loginWithWrongPassword() {
        authService.signUp(EMAIL, NAME, PASSWORD);

        assertTradeException(() -> authService.login(EMAIL, "wrong-password"), ErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("가입하지 않은 이메일이어도 비밀번호가 틀렸을 때와 같은 LOGIN_FAILED")
    void loginWithUnknownEmail() {
        assertTradeException(() -> authService.login("nobody@toss.im", PASSWORD), ErrorCode.LOGIN_FAILED);
    }
}
