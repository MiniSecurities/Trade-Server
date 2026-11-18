package com.sangyunpark99.tradeserver.order.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record FillRequest(

        @NotNull
        @Positive
        Long orderId,

        @NotNull
        @Positive
        Long fillQuantity,

        @NotNull
        @Positive
        BigDecimal fillPrice
) {
}
