package com.sangyunpark99.tradeserver.auth.jwt;

import com.sangyunpark99.tradeserver.auth.UserContext;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtFilterTest {

    private static final String SECRET = "1QtTqI303LMaoF2jONc3puTK2v+ba05VUEkF3V8X998=";

    private final JwtProvider jwtProvider = new JwtProvider(SECRET, Duration.ofMinutes(30));
    private final JwtFilter jwtFilter = new JwtFilter(jwtProvider);

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    private static MockHttpServletRequest request(String uri, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    private static boolean contextIsEmpty() {
        try {
            UserContext.get();
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    @Nested
    @DisplayName("토큰이 올바르면")
    class ValidToken {

        @Test
        @DisplayName("요청 처리 중에는 사용자 ID를 꺼낼 수 있고, 끝나면 지워진다")
        void setsDuringRequestAndClearsAfter() throws Exception {
            String token = jwtProvider.createAccessToken(42L);
            AtomicLong seenUserId = new AtomicLong(-1);
            FilterChain chain = (req, res) -> seenUserId.set(UserContext.get());

            jwtFilter.doFilter(request("/api/orders", "Bearer " + token), new MockHttpServletResponse(), chain);

            assertThat(seenUserId.get()).isEqualTo(42L);
            assertThat(contextIsEmpty()).isTrue();
        }

        @Test
        @DisplayName("요청 처리 중 예외가 나도 사용자 ID는 지워진다")
        void clearsEvenWhenChainThrows() {
            String token = jwtProvider.createAccessToken(42L);
            FilterChain chain = (req, res) -> {
                throw new IllegalStateException("컨트롤러에서 예외");
            };

            assertThatThrownBy(() -> jwtFilter.doFilter(request("/api/orders", "Bearer " + token), new MockHttpServletResponse(), chain))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(contextIsEmpty()).isTrue();
        }

        @Test
        @DisplayName("같은 스레드로 다음 요청이 와도 이전 사용자가 보이지 않는다")
        void nextRequestOnSameThreadDoesNotSeePreviousUser() throws Exception {
            String token = jwtProvider.createAccessToken(42L);
            jwtFilter.doFilter(request("/api/orders", "Bearer " + token), new MockHttpServletResponse(), (req, res) -> {});

            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean chainCalled = new AtomicBoolean(false);
            jwtFilter.doFilter(request("/api/orders", null), response, (req, res) -> chainCalled.set(true));

            assertThat(chainCalled).isFalse();
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(contextIsEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("토큰이 없거나 잘못되면 401로 응답하고 다음 단계로 넘기지 않는다")
    class InvalidToken {

        private void assertRejected(String authorization) throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean chainCalled = new AtomicBoolean(false);

            jwtFilter.doFilter(request("/api/orders", authorization), response, (req, res) -> chainCalled.set(true));

            assertThat(chainCalled).isFalse();
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"");
            assertThat(contextIsEmpty()).isTrue();
        }

        @Test
        @DisplayName("Authorization 헤더가 없음")
        void noHeader() throws Exception {
            assertRejected(null);
        }

        @Test
        @DisplayName("Bearer 형식이 아님")
        void notBearer() throws Exception {
            assertRejected("Basic abc");
        }

        @Test
        @DisplayName("Bearer 뒤에 토큰이 없음")
        void emptyToken() throws Exception {
            assertRejected("Bearer ");
        }

        @Test
        @DisplayName("형식이 깨진 토큰")
        void malformedToken() throws Exception {
            assertRejected("Bearer not-a-jwt");
        }

        @Test
        @DisplayName("만료된 토큰")
        void expiredToken() throws Exception {
            String expired = new JwtProvider(SECRET, Duration.ofSeconds(-1)).createAccessToken(42L);
            assertRejected("Bearer " + expired);
        }

        @Test
        @DisplayName("다른 키로 서명된 토큰")
        void forgedToken() throws Exception {
            String forged = new JwtProvider("Zm9vYmFyZm9vYmFyZm9vYmFyZm9vYmFyZm9vYmFyMTI=", Duration.ofMinutes(30))
                    .createAccessToken(42L);
            assertRejected("Bearer " + forged);
        }
    }

    @Nested
    @DisplayName("인증을 빼는 경로")
    class ExcludedPaths {

        @Test
        @DisplayName("/api/auth/로 시작하는 경로, /api/fills, /actuator/health는 필터를 건너뛴다")
        void excluded() throws Exception {
            assertThat(jwtFilter.shouldNotFilter(request("/api/auth/signup", null))).isTrue();
            assertThat(jwtFilter.shouldNotFilter(request("/api/auth/login", null))).isTrue();
            assertThat(jwtFilter.shouldNotFilter(request("/api/fills", null))).isTrue();
            assertThat(jwtFilter.shouldNotFilter(request("/actuator/health", null))).isTrue();
        }

        @Test
        @DisplayName("이름이 비슷한 경로와 주문 API는 건너뛰지 않는다")
        void notExcluded() throws Exception {
            assertThat(jwtFilter.shouldNotFilter(request("/api/orders", null))).isFalse();
            assertThat(jwtFilter.shouldNotFilter(request("/api/orders/1", null))).isFalse();
            assertThat(jwtFilter.shouldNotFilter(request("/api/authority", null))).isFalse();
            assertThat(jwtFilter.shouldNotFilter(request("/api/fills-admin", null))).isFalse();
            assertThat(jwtFilter.shouldNotFilter(request("/actuator/health/db", null))).isFalse();
            assertThat(jwtFilter.shouldNotFilter(request("/actuator/env", null))).isFalse();
        }
    }
}
