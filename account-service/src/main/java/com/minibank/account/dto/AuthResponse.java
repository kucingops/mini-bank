package com.minibank.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "JWT tokens for the authenticated user")
public class AuthResponse {

    @Schema(description = "JWT access token — paste this into Authorize", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzeWFyaWZAbWluaWJhbmsuY29tIn0.signature")
    private String accessToken;
    @Schema(description = "Opaque refresh token (7 days)", example = "550e8400-e29b-41d4-a716-446655440000")
    private String refreshToken;

    @Schema(description = "Always Bearer", example = "Bearer")
    @Builder.Default
    private String tokenType = "Bearer";

    @Schema(description = "Access token lifetime in seconds", example = "86400")
    private long expiresIn;
    @Schema(description = "Caller's role", example = "USER", allowableValues = {"USER", "ADMIN"})
    private String role;
    @Schema(description = "Caller's bank account ID (null for ADMIN)", example = "3f1c2a9e-8b7d-4c3a-9e2f-1a2b3c4d5e6f")
    private String accountId;
}
