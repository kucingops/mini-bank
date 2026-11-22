package com.minibank.account.controller;

import com.minibank.account.dto.*;
import com.minibank.account.service.AuthService;
import com.minibank.account.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration, login, token refresh, and logout")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a new user",
            description = "Creates a new bank account (balance 0, default daily limit Rp 50.000.000) and user credentials. Returns JWT access and refresh tokens")
    @ApiResponse(responseCode = "201", description = "Registered; tokens returned")
    @ApiResponse(responseCode = "409", description = "Email already registered", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements // public: no token needed
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Login",
            description = "Authenticates with email and password. Returns JWT access token (24h) and refresh token (7 days)")
    @ApiResponse(responseCode = "200", description = "Authenticated; tokens returned")
    @ApiResponse(responseCode = "401", description = "Invalid email or password", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements // public: no token needed
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Refresh access token",
            description = "Exchange a valid refresh token for a new access token and refresh token pair")
    @ApiResponse(responseCode = "200", description = "New token pair returned")
    @ApiResponse(responseCode = "401", description = "Refresh token invalid or expired", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    @SecurityRequirements // public: no token needed
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Logout",
            description = "Revokes all refresh tokens for the authenticated user")
    @ApiResponse(responseCode = "204", description = "Logged out; refresh tokens revoked")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        authService.logout(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
