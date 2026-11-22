package com.minibank.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Refresh token exchange")
public record RefreshTokenRequest(
        @Schema(description = "Refresh token from the login/register response", example = "550e8400-e29b-41d4-a716-446655440000")
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {}
