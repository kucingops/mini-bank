package com.minibank.account.service;

import com.minibank.account.dto.AccountResponse;
import com.minibank.account.dto.CreateAccountRequest;
import com.minibank.account.entity.Account;
import com.minibank.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String BALANCE_CACHE_PREFIX = "account:balance:";
    private static final Duration BALANCE_CACHE_TTL = Duration.ofMinutes(10);

    /**
     * Create a new bank account.
     * Demonstrates Spring IoC — AccountRepository and RedisTemplate injected via constructor.
     */
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
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
        log.info("Account created: {} for {}", account.getAccountNumber(), account.getHolderName());

        // Cache the initial balance
        cacheBalance(account.getId(), account.getBalance());

        return toResponse(account);
    }

    /**
     * Get account by ID with Redis balance caching.
     */
    public AccountResponse getAccount(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));
        return toResponse(account);
    }

    /**
     * Get account balance — first checks Redis cache, then falls back to DB.
     * Demonstrates Redis Caching Strategy.
     */
    public BigDecimal getBalance(UUID accountId) {
        String cacheKey = BALANCE_CACHE_PREFIX + accountId;

        // Try cache first
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Balance cache HIT for account {}", accountId);
            return new BigDecimal(cached.toString());
        }

        // Cache miss — fetch from DB
        log.debug("Balance cache MISS for account {}", accountId);
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));

        cacheBalance(accountId, account.getBalance());
        return account.getBalance();
    }

    /**
     * Get all accounts.
     * Demonstrates Java Stream — mapping entity list to DTO list.
     */
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get account by account number.
     */
    public AccountResponse getAccountByNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountNumber));
        return toResponse(account);
    }

    /**
     * Invalidate balance cache — called after transfer completion.
     */
    public void invalidateBalanceCache(UUID accountId) {
        String cacheKey = BALANCE_CACHE_PREFIX + accountId;
        redisTemplate.delete(cacheKey);
        log.debug("Balance cache invalidated for account {}", accountId);
    }

    // ==================== Private Helpers ====================

    private void cacheBalance(UUID accountId, BigDecimal balance) {
        String cacheKey = BALANCE_CACHE_PREFIX + accountId;
        redisTemplate.opsForValue().set(cacheKey, balance.toString(), BALANCE_CACHE_TTL);
    }

    private String generateAccountNumber() {
        String accountNumber;
        do {
            accountNumber = String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    private AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .holderName(account.getHolderName())
                .email(account.getEmail())
                .balance(account.getBalance())
                .dailyTransferLimit(account.getDailyTransferLimit())
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
