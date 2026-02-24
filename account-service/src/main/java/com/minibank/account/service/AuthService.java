package com.minibank.account.service;

import com.minibank.account.dto.*;
import com.minibank.account.entity.Account;
import com.minibank.account.entity.RefreshToken;
import com.minibank.account.entity.UserCredential;
import com.minibank.account.repository.AccountRepository;
import com.minibank.account.repository.RefreshTokenRepository;
import com.minibank.account.repository.UserCredentialRepository;
import com.minibank.account.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserCredentialRepository userCredentialRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * Register a new user — creates both Account and UserCredential.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check if email already exists
        if (userCredentialRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered: " + request.getEmail());
        }

        // Create the bank account
        String accountNumber = generateAccountNumber();
        Account account = Account.builder()
                .accountNumber(accountNumber)
                .holderName(request.getHolderName())
                .email(request.getEmail())
                .balance(request.getInitialBalance() != null ? request.getInitialBalance() : BigDecimal.ZERO)
                .dailyTransferLimit(request.getDailyTransferLimit() != null ?
                        request.getDailyTransferLimit() : new BigDecimal("50000000"))
                .status("ACTIVE")
                .build();
        account = accountRepository.save(account);

        // Create user credentials
        UserCredential credential = UserCredential.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("USER")
                .accountId(account.getId())
                .build();
        credential = userCredentialRepository.save(credential);

        log.info("User registered: {} with account: {}", credential.getEmail(), account.getAccountNumber());

        // Generate tokens
        return generateAuthResponse(credential);
    }

    /**
     * Login — validates credentials and returns JWT tokens.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        UserCredential credential = userCredentialRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), credential.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }

        log.info("User logged in: {}", credential.getEmail());

        // Clean old refresh tokens and generate new ones
        refreshTokenRepository.deleteByUserId(credential.getId());
        return generateAuthResponse(credential);
    }

    /**
     * Refresh — validates refresh token and issues new access token.
     */
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new RuntimeException("Refresh token expired");
        }

        UserCredential credential = userCredentialRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Delete old refresh token and create new one
        refreshTokenRepository.delete(refreshToken);

        log.info("Token refreshed for user: {}", credential.getEmail());
        return generateAuthResponse(credential);
    }

    /**
     * Logout — deletes all refresh tokens for the user.
     */
    @Transactional
    public void logout(String email) {
        UserCredential credential = userCredentialRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        refreshTokenRepository.deleteByUserId(credential.getId());
        log.info("User logged out: {}", email);
    }

    // ==================== Private Helpers ====================

    private AuthResponse generateAuthResponse(UserCredential credential) {
        String accessToken = jwtUtil.generateAccessToken(
                credential.getEmail(), credential.getRole(), credential.getAccountId());

        String refreshTokenStr = jwtUtil.generateRefreshToken();

        // Save refresh token to DB
        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenStr)
                .userId(credential.getId())
                .expiresAt(LocalDateTime.now().plusSeconds(jwtUtil.getRefreshTokenExpiration() / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getAccessTokenExpirationSeconds())
                .role(credential.getRole())
                .accountId(credential.getAccountId() != null ? credential.getAccountId().toString() : null)
                .build();
    }

    private String generateAccountNumber() {
        String accountNumber;
        do {
            accountNumber = String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }
}
