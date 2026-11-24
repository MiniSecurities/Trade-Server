package com.sangyunpark99.tradeserver.auth.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank
        @Email
        String email,

        @NotBlank
        String name,

        @NotBlank
        @Size(min = 8)
        String password
) {
}
