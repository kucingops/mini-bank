package com.minibank.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Login credentials")
public record LoginRequest(
        @Schema(description = "Registered email", example = "syarif@minibank.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @Schema(description = "Password", example = "password123", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "Password is required")
        String password
) {}
