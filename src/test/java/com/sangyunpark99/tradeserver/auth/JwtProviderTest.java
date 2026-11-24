package com.sangyunpark99.tradeserver.auth;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final String SECRET = "1QtTqI303LMaoF2jONc3puTK2v+ba05VUEkF3V8X998=";
    private static final String OTHER_SECRET = "Zm9vYmFyZm9vYmFyZm9vYmFyZm9vYmFyZm9vYmFyMTI=";

    @Test
    @DisplayName("발급한 토큰에서 같은 사용자 ID를 꺼낼 수 있다")
    void createAndParse() {
        JwtProvider jwtProvider = new JwtProvider(SECRET, Duration.ofMinutes(30));

        String token = jwtProvider.createAccessToken(42L);

        assertThat(jwtProvider.getUserId(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("만료된 토큰은 꺼낼 수 없다")
    void expiredToken() {
        JwtProvider jwtProvider = new JwtProvider(SECRET, Duration.ofSeconds(-1));

        String token = jwtProvider.createAccessToken(42L);

        assertThatThrownBy(() -> jwtProvider.getUserId(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("다른 키로 서명한 토큰은 꺼낼 수 없다")
    void tokenSignedWithOtherKey() {
        JwtProvider issuer = new JwtProvider(OTHER_SECRET, Duration.ofMinutes(30));
        JwtProvider verifier = new JwtProvider(SECRET, Duration.ofMinutes(30));

        String token = issuer.createAccessToken(42L);

        assertThatThrownBy(() -> verifier.getUserId(token)).isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("32바이트보다 짧은 키로는 만들 수 없다")
    void rejectShortKey() {
        assertThatThrownBy(() -> new JwtProvider("c2hvcnQta2V5", Duration.ofMinutes(30)))
                .isInstanceOf(io.jsonwebtoken.security.WeakKeyException.class);
    }
}
