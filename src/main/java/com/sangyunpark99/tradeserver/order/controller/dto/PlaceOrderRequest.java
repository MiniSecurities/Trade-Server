package com.sangyunpark99.tradeserver.order.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PlaceOrderRequest(

        @NotNull
        @Positive
        Long accountId,

        @NotBlank
        String symbol,

        @NotNull
        @Positive
        Long quantity,

        @NotNull
        @Positive
        BigDecimal limitPrice
) {
}
