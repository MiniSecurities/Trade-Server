package com.sangyunpark99.tradeserver.auth;

import com.sangyunpark99.tradeserver.common.exception.ErrorCode;
import com.sangyunpark99.tradeserver.common.exception.TradeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserContextTest {

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    private static void assertUnauthorized() {
        assertThatThrownBy(UserContext::get)
                .isInstanceOfSatisfying(TradeException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("넣은 사용자 ID를 꺼낼 수 있다")
    void setAndGet() {
        UserContext.set(1L);

        assertThat(UserContext.get()).isEqualTo(1L);
    }

    @Test
    @DisplayName("넣은 적이 없으면 UNAUTHORIZED")
    void getWithoutSet() {
        assertUnauthorized();
    }

    @Test
    @DisplayName("지운 뒤에는 꺼낼 수 없다")
    void removeClears() {
        UserContext.set(1L);

        UserContext.remove();

        assertUnauthorized();
    }

    @Test
    @DisplayName("다른 스레드에서 넣은 값은 보이지 않는다")
    void isolatedPerThread() throws Exception {
        UserContext.set(1L);
        AtomicReference<Throwable> seenInOtherThread = new AtomicReference<>();

        Thread other = new Thread(() -> {
            try {
                UserContext.get();
            } catch (Throwable t) {
                seenInOtherThread.set(t);
            }
        });
        other.start();
        other.join();

        assertThat(seenInOtherThread.get()).isInstanceOf(TradeException.class);
        assertThat(UserContext.get()).isEqualTo(1L);
    }
}
