package com.sangyunpark99.tradeserver.user;

import com.sangyunpark99.tradeserver.common.exception.ErrorCode;
import com.sangyunpark99.tradeserver.common.exception.TradeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static final String EMAIL = "user@toss.im";
    private static final String NAME = "박상윤";
    private static final String PASSWORD_HASH = "$2a$10$hash";

    private static void assertError(Executable executable, ErrorCode errorCode) {
        assertThatThrownBy(executable::execute)
                .isInstanceOfSatisfying(TradeException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }

    @Test
    @DisplayName("사용자는 이메일, 이름, 비밀번호 해시를 가지고 만들어진 시각이 기록된다")
    void create() {
        LocalDateTime before = LocalDateTime.now();

        User user = new User(EMAIL, NAME, PASSWORD_HASH);

        assertThat(user.getEmail()).isEqualTo(EMAIL);
        assertThat(user.getName()).isEqualTo(NAME);
        assertThat(user.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(user.getCreatedAt()).isNotNull().isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("이메일이 null이거나 비어 있으면 만들 수 없다")
    void rejectBlankEmail() {
        assertError(() -> new User(null, NAME, PASSWORD_HASH), ErrorCode.INVALID_EMAIL);
        assertError(() -> new User(" ", NAME, PASSWORD_HASH), ErrorCode.INVALID_EMAIL);
    }

    @Test
    @DisplayName("이름이 null이거나 비어 있으면 만들 수 없다")
    void rejectBlankName() {
        assertError(() -> new User(EMAIL, null, PASSWORD_HASH), ErrorCode.INVALID_NAME);
        assertError(() -> new User(EMAIL, " ", PASSWORD_HASH), ErrorCode.INVALID_NAME);
    }

    @Test
    @DisplayName("비밀번호 해시가 null이거나 비어 있으면 만들 수 없다")
    void rejectBlankPasswordHash() {
        assertError(() -> new User(EMAIL, NAME, null), ErrorCode.INVALID_PASSWORD);
        assertError(() -> new User(EMAIL, NAME, " "), ErrorCode.INVALID_PASSWORD);
    }
}
