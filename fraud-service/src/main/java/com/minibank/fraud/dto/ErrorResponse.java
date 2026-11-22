package com.minibank.fraud.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Standard error body returned by every endpoint of this service,
 * including authentication (401) and authorization (403) failures.
 */
@Schema(name = "ErrorResponse", description = "Standard error body")
public record ErrorResponse(
        @Schema(description = "HTTP status code", example = "403")
        int status,

        @Schema(description = "HTTP reason phrase", example = "Forbidden")
        String error,

        @Schema(description = "Human-readable explanation", example = "You do not have access to this account")
        String message,

        @Schema(description = "Request path", example = "/api/v1/accounts/3f1c2a9e-8b7d-4c3a-9e2f-1a2b3c4d5e6f")
        String path,

        @Schema(description = "When the error happened (server time)", example = "2026-11-22T10:15:30")
        LocalDateTime timestamp
) {
}
