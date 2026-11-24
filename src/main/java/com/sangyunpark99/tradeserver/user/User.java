package com.sangyunpark99.tradeserver.user;

import com.sangyunpark99.tradeserver.common.exception.ErrorCode;
import com.sangyunpark99.tradeserver.common.exception.TradeException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public User(String email, String name, String passwordHash) {
        checkEmail(email);
        checkName(name);
        checkPasswordHash(passwordHash);
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
        this.createdAt = LocalDateTime.now();
    }

    private void checkEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new TradeException(ErrorCode.INVALID_EMAIL);
        }
    }

    private void checkName(String name) {
        if (name == null || name.isBlank()) {
            throw new TradeException(ErrorCode.INVALID_NAME);
        }
    }

    private void checkPasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new TradeException(ErrorCode.INVALID_PASSWORD);
        }
    }
}
