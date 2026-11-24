package com.sangyunpark99.tradeserver.account.controller.dto;

import java.math.BigDecimal;

public record DepositAccountResponse(
        BigDecimal balance,
        BigDecimal availableBalance
) {
}
