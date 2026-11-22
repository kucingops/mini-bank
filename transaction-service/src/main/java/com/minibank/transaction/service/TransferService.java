package com.minibank.transaction.service;

import com.minibank.transaction.dto.PageResponse;
import com.minibank.transaction.dto.TransferRequest;
import com.minibank.transaction.dto.TransferResponse;
import com.minibank.transaction.entity.Account;
import com.minibank.transaction.entity.Transaction;
import com.minibank.transaction.exception.IdempotencyConflictException;
import com.minibank.transaction.repository.AccountRepository;
import com.minibank.transaction.repository.TransactionRepository;
import com.minibank.transaction.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private static final long LOCK_WAIT_SECONDS = 5;
    private static final long LOCK_LEASE_SECONDS = 10;

    /**
     * Process a transfer request:
     * 1. Verify the caller owns the source account
     * 2. Acquire distributed lock on the source account
     * 3. Check available balance (balance minus pending) and daily limit
     * 3. Insert PENDING transaction
     * 4. Publish TransferRequested event to Redis Stream
     * 5. Return 202 Accepted
     */
    public TransferResult initiateTransfer(TransferRequest request, String idempotencyKey) {
        validateSameAccount(request);
        validateIdempotencyKey(idempotencyKey);

        // Only the owner of the source account may move money out of it
        if (!SecurityUtils.isOwner(request.getFromAccountId())) {
            throw new AccessDeniedException("You can only transfer from your own account");
        }

        // Lock the source account: every check below (balance, pending, daily limit)
        // is about the sender, so all transfers out of one account must be serialized,
        // regardless of destination. Credits are atomic SQL and need no lock.
        RLock lock = redissonClient.getLock(LOCK_PREFIX + request.getFromAccountId());

        try {
            if (!lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS)) {
                throw new RuntimeException("Could not acquire lock for transfer. Please try again.");
            }

            log.info("Distributed lock acquired for source account {}", request.getFromAccountId());

            // Idempotency: a retry with the same key returns the original transfer instead of
            // creating a second one. Checked under the source-account lock, so concurrent
            // retries are serialized; the unique constraint on (from_account_id, idempotency_key)
            // is the backstop.
            String requestHash = idempotencyKey != null ? fingerprint(request) : null;
            if (idempotencyKey != null) {
                Optional<Transaction> existing = transactionRepository
                        .findByFromAccountIdAndIdempotencyKey(request.getFromAccountId(), idempotencyKey);
                if (existing.isPresent()) {
                    if (!requestHash.equals(existing.get().getRequestHash())) {
                        throw new IdempotencyConflictException(
                                "Idempotency-Key '" + idempotencyKey + "' was already used for a different transfer request");
                    }
                    log.info("Idempotent replay of transfer {} for key {}",
                            existing.get().getReferenceNo(), idempotencyKey);
                    return new TransferResult(toResponse(existing.get()), true);
                }
            }

            // Validate accounts exist and are active
            Account fromAccount = accountRepository.findById(request.getFromAccountId())
                    .orElseThrow(() -> new RuntimeException("Source account not found"));
            Account toAccount = accountRepository.findById(request.getToAccountId())
                    .orElseThrow(() -> new RuntimeException("Destination account not found"));

            if (!"ACTIVE".equals(fromAccount.getStatus()) || !"ACTIVE".equals(toAccount.getStatus())) {
                throw new RuntimeException("Both accounts must be ACTIVE");
            }

            // Check available balance — funds held by PENDING transfers are not spendable
            BigDecimal pending = transactionRepository.sumPendingOutgoing(request.getFromAccountId());
            BigDecimal available = fromAccount.getBalance().subtract(pending);
            if (available.compareTo(request.getAmount()) < 0) {
                throw new RuntimeException("Insufficient balance. Available: " + available);
            }

            // Check daily transfer limit (PENDING + COMPLETED today)
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
                    .idempotencyKey(idempotencyKey)
                    .requestHash(requestHash)
                    .build();

            transaction = transactionRepository.save(transaction);
            log.info("Transaction {} created with status PENDING", referenceNo);

            // Publish to Redis Stream (replaces Kafka producer)
            publishTransferRequested(transaction);

            return new TransferResult(toResponse(transaction), false);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Transfer interrupted", e);
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
            log.debug("Distributed lock released");
        }
    }

    /**
     * Get transfer by ID.
     */
    public TransferResponse getTransfer(UUID transactionId) {
        Transaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));
        if (!SecurityUtils.isAdmin()
                && !SecurityUtils.isOwner(txn.getFromAccountId())
                && !SecurityUtils.isOwner(txn.getToAccountId())) {
            throw new AccessDeniedException("You do not have access to this transaction");
        }
        return toResponse(txn);
    }

    /**
     * Get paginated transaction history for an account (sent and received).
     * Demonstrates Java Stream API — mapping the page's entities to DTOs.
     */
    public PageResponse<TransferResponse> getTransactionHistory(UUID accountId, Pageable pageable) {
        SecurityUtils.requireOwnerOrAdmin(accountId);
        Page<Transaction> page = transactionRepository.findHistory(accountId, pageable);

        List<TransferResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return PageResponse.of(new PageImpl<>(content, pageable, page.getTotalElements()));
    }

    /**
     * Complete a validated transfer — called when fraud check passes.
     * Executes atomic debit and credit operations.
     *
     * Idempotent: the PENDING -> COMPLETED transition is a compare-and-set, so a
     * redelivered or duplicated event finds the row no longer PENDING and does nothing.
     * The row lock taken by that UPDATE also serializes concurrent duplicates.
     */
    @Transactional
    public void completeTransfer(UUID transactionId) {
        Transaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        int claimed = transactionRepository.transitionStatus(transactionId, "PENDING", "COMPLETED", "PASSED");
        if (claimed == 0) {
            log.warn("Transfer {} is no longer PENDING — ignoring duplicate completion event", txn.getReferenceNo());
            return;
        }

        // Atomic debit
        int debited = accountRepository.debitBalance(txn.getFromAccountId(), txn.getAmount());
        if (debited == 0) {
            transactionRepository.updateStatus(transactionId, "FAILED", "PASSED");
            log.error("Insufficient balance for transaction {}", txn.getReferenceNo());
            return;
        }

        // Atomic credit
        accountRepository.creditBalance(txn.getToAccountId(), txn.getAmount());

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
        int updated = transactionRepository.transitionStatus(transactionId, "PENDING", "REJECTED", "FLAGGED");
        if (updated == 0) {
            log.warn("Transfer {} is no longer PENDING — ignoring duplicate rejection event", transactionId);
            return;
        }
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

    // ==================== Daily Limit ====================

    /**
     * Check the daily transfer limit. Always computed from the database (PENDING + COMPLETED
     * transfers since midnight) while holding the source-account lock, so concurrent
     * requests cannot each see a stale total and jointly exceed the limit.
     */
    private void checkDailyLimit(UUID accountId, BigDecimal transferAmount, BigDecimal dailyLimit) {
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        BigDecimal usedToday = transactionRepository.sumDailyTransfers(accountId, startOfDay);

        if (usedToday.add(transferAmount).compareTo(dailyLimit) > 0) {
            throw new RuntimeException(String.format(
                    "Daily transfer limit exceeded. Limit: %s, Used today: %s, Requested: %s",
                    dailyLimit, usedToday, transferAmount));
        }
    }

    private void invalidateBalanceCache(UUID accountId) {
        redisTemplate.delete("account:balance:" + accountId);
    }

    // ==================== Helpers ====================

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    private void validateIdempotencyKey(String key) {
        if (key == null) {
            return;
        }
        if (key.isBlank() || key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must be 1-" + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
    }

    /**
     * SHA-256 over the fields that define a transfer. Amount is normalized so that
     * 1000000 and 1000000.00 count as the same request.
     */
    static String fingerprint(TransferRequest request) {
        String canonical = String.join("|",
                String.valueOf(request.getFromAccountId()),
                String.valueOf(request.getToAccountId()),
                request.getAmount().stripTrailingZeros().toPlainString(),
                request.getDescription() != null ? request.getDescription() : "");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void validateSameAccount(TransferRequest request) {
        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new RuntimeException("Cannot transfer to the same account");
        }
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
