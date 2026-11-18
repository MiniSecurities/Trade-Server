package com.sangyunpark99.tradeserver.account;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(precision = 19, scale = 4)
    private BigDecimal reservedBalance;

    public Account(BigDecimal balance) {
        checkBalance(balance);
        this.balance = balance;
        this.reservedBalance = BigDecimal.ZERO;
    }

    public BigDecimal getAvailableBalance() {
        return this.balance.subtract(this.reservedBalance);
    }

    private void checkBalance(BigDecimal balance) {
        if(balance == null || balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("balance 값이 올바르지 않습니다.");
        }
    }
}