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

    public void reserve(BigDecimal balance) {
        checkAmount(balance) ;
        checkReservedBalance(balance);
        this.reservedBalance = this.reservedBalance.add(balance);
    }

    public BigDecimal getAvailableBalance() {
        return this.balance.subtract(this.reservedBalance);
    }

    private void checkBalance(BigDecimal amount) {
        if(amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("balance 값이 올바르지 않습니다.");
        }
    }

     private void checkAmount(BigDecimal amount) {
        if(amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("묶을 금액은 0보다 커야 합니다.");
        }
    }

    private void checkReservedBalance(BigDecimal reservedAmount) {
        BigDecimal availableBalance = getAvailableBalance();
        if(availableBalance.compareTo(reservedAmount) < 0) {
            throw new IllegalArgumentException("주문 금액이 잔액보다 큽니다.");
        }
    }
}