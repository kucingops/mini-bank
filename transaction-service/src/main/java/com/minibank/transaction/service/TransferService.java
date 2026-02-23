package com.minibank.transaction.service;

import com.minibank.transaction.dto.TransferRequest;
import com.minibank.transaction.dto.TransferResponse;
import com.minibank.transaction.entity.Account;
import com.minibank.transaction.entity.Transaction;
import com.minibank.transaction.repository.AccountRepository;
import com.minibank.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Transfer Service — core business logic for fund transfers.
 * 
 * Demonstrates:
 * - Spring IoC (constructor injection of multiple dependencies)
 * - Redis Distributed Lock (Redisson) for preventing double-spending
 * - Redis Caching (daily transfer limit tracking)
 * - Redis Streams (event publishing, replacing Kafka producer)
 * - Java Stream API (data transformation and filtering)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final RedissonClient redissonClient;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${redis.stream.transfer-requested}")
    private String transferRequestedStream;

    private static final String LOCK_PREFIX = "lock:account:";
    private static final String DAILY_LIMIT_PREFIX = "daily-limit:";
    private static final Duration DAILY_LIMIT_TTL = Duration.ofHours(24);
    private static final long LOCK_WAIT_SECONDS = 5;
    private static final long LOCK_LEASE_SECONDS = 10;

    /**
     * Process a transfer request:
     * 1. Acquire distributed lock on both accounts
     * 2. Check daily transfer limit (from Redis cache)
     * 3. Insert PENDING transaction
     * 4. Publish TransferRequested event to Redis Stream
     * 5. Return 202 Accepted
     */
    public TransferResponse initiateTransfer(TransferRequest request) {
        validateSameAccount(request);

        // Get sorted lock keys to prevent deadlock
        String lockKey1 = LOCK_PREFIX + min(request.getFromAccountId(), request.getToAccountId());
        String lockKey2 = LOCK_PREFIX + max(request.getFromAccountId(), request.getToAccountId());

        RLock lock1 = redissonClient.getLock(lockKey1);
        RLock lock2 = redissonClient.getLock(lockKey2);

        try {
            // Acquire distributed locks — prevents double spending
            boolean locked1 = lock1.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            boolean locked2 = lock2.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);

            if (!locked1 || !locked2) {
                throw new RuntimeException("Could not acquire locks for transfer. Please try again.");
            }

            log.info("Distributed lock acquired for accounts {} and {}",
                    request.getFromAccountId(), request.getToAccountId());

            // Validate accounts exist and are active
            Account fromAccount = accountRepository.findById(request.getFromAccountId())
                    .orElseThrow(() -> new RuntimeException("Source account not found"));
            Account toAccount = accountRepository.findById(request.getToAccountId())
                    .orElseThrow(() -> new RuntimeException("Destination account not found"));

            if (!"ACTIVE".equals(fromAccount.getStatus()) || !"ACTIVE".equals(toAccount.getStatus())) {
                throw new RuntimeException("Both accounts must be ACTIVE");
            }

            // Check balance
            if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
                throw new RuntimeException("Insufficient balance. Available: " + fromAccount.getBalance());
            }

            // Check daily transfer limit (Redis Cache)
            checkDailyLimit(request.getFromAccountId(), request.getAmount(), fromAccount.getDailyTransferLimit());

            // Create PENDING transaction
            String referenceNo = generateReferenceNo();
            Transaction transaction = Transaction.builder()
                    .referenceNo(referenceNo)
                    .fromAccountId(request.getFromAccountId())
                    .toAccountId(request.getToAccountId())
                    .amount(request.getAmount())
                    .type("TRANSFER")
                    .status("PENDING")
                    .fraudCheckStatus("PENDING")
                    .description(request.getDescription())
                    .build();

            transaction = transactionRepository.save(transaction);
            log.info("Transaction {} created with status PENDING", referenceNo);

            // Publish to Redis Stream (replaces Kafka producer)
            publishTransferRequested(transaction);

            return toResponse(transaction);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Transfer interrupted", e);
        } finally {
            if (lock1.isHeldByCurrentThread()) lock1.unlock();
            if (lock2.isHeldByCurrentThread()) lock2.unlock();
            log.debug("Distributed locks released");
        }
    }

    /**
     * Get transfer by ID.
     */
    public TransferResponse getTransfer(UUID transactionId) {
        Transaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));
        return toResponse(txn);
    }

    /**
     * Get transaction history for an account.
     * Demonstrates Java Stream API — filtering, sorting, and mapping.
     */
    public List<TransferResponse> getTransactionHistory(UUID accountId) {
        List<Transaction> transactions = transactionRepository
                .findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(accountId, accountId);

        // Java Stream: filter out cancelled, sort, map to DTO
        return transactions.stream()
                .filter(t -> !"CANCELLED".equals(t.getStatus()))
                .sorted(Comparator.comparing(Transaction::getCreatedAt).reversed())
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Complete a validated transfer — called when fraud check passes.
     * Executes atomic debit and credit operations.
     */
    @Transactional
    public void completeTransfer(UUID transactionId) {
        Transaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        // Atomic debit
        int debited = accountRepository.debitBalance(txn.getFromAccountId(), txn.getAmount());
        if (debited == 0) {
            transactionRepository.updateStatus(transactionId, "FAILED", "PASSED");
            log.error("Insufficient balance for transaction {}", txn.getReferenceNo());
            return;
        }

        // Atomic credit
        accountRepository.creditBalance(txn.getToAccountId(), txn.getAmount());

        // Update transaction status
        transactionRepository.updateStatus(transactionId, "COMPLETED", "PASSED");

        // Update daily limit cache
        updateDailyLimitCache(txn.getFromAccountId(), txn.getAmount());

        // Invalidate balance cache
        invalidateBalanceCache(txn.getFromAccountId());
        invalidateBalanceCache(txn.getToAccountId());

        log.info("Transfer {} COMPLETED: {} from {} to {}",
                txn.getReferenceNo(), txn.getAmount(), txn.getFromAccountId(), txn.getToAccountId());
    }

    /**
     * Reject a transfer — called when fraud check fails.
     */
    @Transactional
    public void rejectTransfer(UUID transactionId) {
        transactionRepository.updateStatus(transactionId, "REJECTED", "FLAGGED");
        log.warn("Transfer {} REJECTED by fraud detection", transactionId);
    }

    // ==================== Redis Streams (replaces Kafka) ====================

    /**
     * Publish TransferRequested event to Redis Stream.
     * This is equivalent to Kafka producer.publish("transfer-requested", event).
     */
    private void publishTransferRequested(Transaction txn) {
        Map<String, String> eventData = Map.of(
                "transactionId", txn.getId().toString(),
                "referenceNo", txn.getReferenceNo(),
                "fromAccountId", txn.getFromAccountId().toString(),
                "toAccountId", txn.getToAccountId().toString(),
                "amount", txn.getAmount().toString(),
                "description", txn.getDescription() != null ? txn.getDescription() : "",
                "timestamp", LocalDateTime.now().toString()
        );

        RecordId recordId = redisTemplate.opsForStream()
                .add(StreamRecords.newRecord()
                        .in(transferRequestedStream)
                        .ofMap(eventData));

        log.info("Published TransferRequested event to stream '{}', recordId: {}",
                transferRequestedStream, recordId);
    }

    // ==================== Daily Limit (Redis Cache) ====================

    /**
     * Check daily transfer limit from Redis cache.
     * Avoids querying the database for every transfer validation.
     */
    private void checkDailyLimit(UUID accountId, BigDecimal transferAmount, BigDecimal dailyLimit) {
        String cacheKey = DAILY_LIMIT_PREFIX + accountId + ":" + LocalDate.now();
        Object cached = redisTemplate.opsForValue().get(cacheKey);

        BigDecimal usedToday;
        if (cached != null) {
            usedToday = new BigDecimal(cached.toString());
            log.debug("Daily limit cache HIT: {} already used today", usedToday);
        } else {
            // Cache miss — calculate from DB
            LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
            usedToday = transactionRepository.sumDailyTransfers(accountId, startOfDay);
            log.debug("Daily limit cache MISS: {} calculated from DB", usedToday);
        }

        if (usedToday.add(transferAmount).compareTo(dailyLimit) > 0) {
            throw new RuntimeException(String.format(
                    "Daily transfer limit exceeded. Limit: %s, Used today: %s, Requested: %s",
                    dailyLimit, usedToday, transferAmount));
        }
    }

    private void updateDailyLimitCache(UUID accountId, BigDecimal amount) {
        String cacheKey = DAILY_LIMIT_PREFIX + accountId + ":" + LocalDate.now();
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        BigDecimal newTotal = cached != null ?
                new BigDecimal(cached.toString()).add(amount) : amount;
        redisTemplate.opsForValue().set(cacheKey, newTotal.toString(), DAILY_LIMIT_TTL);
    }

    private void invalidateBalanceCache(UUID accountId) {
        redisTemplate.delete("account:balance:" + accountId);
    }

    // ==================== Helpers ====================

    private void validateSameAccount(TransferRequest request) {
        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new RuntimeException("Cannot transfer to the same account");
        }
    }

    private UUID min(UUID a, UUID b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private UUID max(UUID a, UUID b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private String generateReferenceNo() {
        return "TXN" + System.currentTimeMillis() + String.format("%04d", new Random().nextInt(10000));
    }

    private TransferResponse toResponse(Transaction txn) {
        return TransferResponse.builder()
                .transactionId(txn.getId())
                .referenceNo(txn.getReferenceNo())
                .fromAccountId(txn.getFromAccountId())
                .toAccountId(txn.getToAccountId())
                .amount(txn.getAmount())
                .status(txn.getStatus())
                .fraudCheckStatus(txn.getFraudCheckStatus())
                .description(txn.getDescription())
                .createdAt(txn.getCreatedAt())
                .build();
    }
}
