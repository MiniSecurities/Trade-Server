package com.sangyunpark99.tradeserver.account.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record DepositAccountRequest(

        @NotNull
        @Positive
        BigDecimal amount
) {
}
