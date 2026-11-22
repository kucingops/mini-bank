package com.minibank.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Current balance of an account (served from Redis cache when available)")
public record BalanceResponse(
        @Schema(description = "Account ID", example = "3f1c2a9e-8b7d-4c3a-9e2f-1a2b3c4d5e6f")
        UUID accountId,

        @Schema(description = "Balance in IDR", example = "99000000.00")
        BigDecimal balance
) {
}
