package com.sangyunpark99.tradeserver.auth.controller.dto;

public record LoginResponse(
        String accessToken,
        long expiresIn
) {
}
