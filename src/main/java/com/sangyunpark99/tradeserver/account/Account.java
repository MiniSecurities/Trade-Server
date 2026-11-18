package com.sangyunpark99.tradeserver.account;

import com.sangyunpark99.tradeserver.common.exception.ErrorCode;
import com.sangyunpark99.tradeserver.common.exception.TradeException;
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
        checkNotNegative(balance);
        this.balance = balance;
        this.reservedBalance = BigDecimal.ZERO;
    }

    public void reserve(BigDecimal amount) {
        checkPositive(amount);
        checkWithinAvailable(amount);
        this.reservedBalance = this.reservedBalance.add(amount);
    }

    public void applyFill(BigDecimal amount) {
        checkPositive(amount);
        checkWithinReserved(amount);
        this.balance = this.balance.subtract(amount);
        this.reservedBalance = this.reservedBalance.subtract(amount);
    }

    public void release(BigDecimal amount) {
        checkPositive(amount);
        checkWithinReserved(amount);
        this.reservedBalance = this.reservedBalance.subtract(amount);
    }

    public BigDecimal getAvailableBalance() {
        return this.balance.subtract(this.reservedBalance);
    }

    private void checkNotNegative(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new TradeException(ErrorCode.INVALID_BALANCE);
        }
    }

    private void checkPositive(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TradeException(ErrorCode.INVALID_AMOUNT);
        }
    }

    private void checkWithinAvailable(BigDecimal amount) {
        if (amount.compareTo(getAvailableBalance()) > 0) {
            throw new TradeException(ErrorCode.INSUFFICIENT_AVAILABLE_BALANCE);
        }
    }

    private void checkWithinReserved(BigDecimal amount) {
        if (amount.compareTo(this.reservedBalance) > 0) {
            throw new TradeException(ErrorCode.EXCEED_RESERVED_BALANCE);
        }
    }
}
