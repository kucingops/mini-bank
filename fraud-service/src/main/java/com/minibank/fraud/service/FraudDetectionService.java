package com.minibank.fraud.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Fraud Detection Service — rule-based anomaly detection.
 * 
 * Analyzes each transfer request against configurable rules:
 * 1. Large amount to new recipient
 * 2. High frequency transfers (rate limiting)
 * 3. Suspicious hours (midnight to dawn)
 * 4. Velocity check (rapid consecutive transfers)
 * 
 * Demonstrates:
 * - Spring IoC (injected configuration + dependencies)
 * - Redis for rate limiting / velocity tracking
 */
@Service
@Slf4j
public class FraudDetectionService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${fraud.rules.large-amount-threshold}")
    private long largeAmountThreshold;

    @Value("${fraud.rules.max-transfers-per-hour}")
    private int maxTransfersPerHour;

    @Value("${fraud.rules.suspicious-hours-start}")
    private int suspiciousHoursStart;

    @Value("${fraud.rules.suspicious-hours-end}")
    private int suspiciousHoursEnd;

    private static final String TRANSFER_COUNT_PREFIX = "fraud:transfer-count:";

    public FraudDetectionService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Analyze a transfer request for fraud indicators.
     * Returns a FraudResult with risk score and details.
     */
    public FraudResult analyze(Map<String, String> transferEvent) {
        String fromAccountId = transferEvent.get("fromAccountId");
        String toAccountId = transferEvent.get("toAccountId");
        BigDecimal amount = new BigDecimal(transferEvent.get("amount"));
        String transactionId = transferEvent.get("transactionId");

        int riskScore = 0;
        StringBuilder details = new StringBuilder();

        // Rule 1: Large amount transfer
        if (amount.compareTo(BigDecimal.valueOf(largeAmountThreshold)) > 0) {
            riskScore += 30;
            details.append("LARGE_AMOUNT: Transfer Rp ").append(amount)
                    .append(" exceeds threshold Rp ").append(largeAmountThreshold).append(". ");
            log.warn("Fraud Rule 1 triggered: Large amount {} for txn {}", amount, transactionId);
        }

        // Rule 2: High frequency — more than N transfers in 1 hour
        int recentCount = getRecentTransferCount(fromAccountId);
        if (recentCount >= maxTransfersPerHour) {
            riskScore += 40;
            details.append("HIGH_FREQUENCY: ").append(recentCount)
                    .append(" transfers in the last hour (max: ").append(maxTransfersPerHour).append("). ");
            log.warn("Fraud Rule 2 triggered: {} recent transfers for account {}", recentCount, fromAccountId);
        }

        // Rule 3: Suspicious hours (00:00 - 05:00)
        int currentHour = LocalDateTime.now().getHour();
        if (currentHour >= suspiciousHoursStart && currentHour < suspiciousHoursEnd) {
            riskScore += 20;
            details.append("SUSPICIOUS_HOUR: Transfer at ").append(currentHour).append(":00. ");
            log.warn("Fraud Rule 3 triggered: Transfer at suspicious hour {} for txn {}", currentHour, transactionId);
        }

        // Rule 4: Velocity check — consecutive transfers to same destination
        if (hasRecentTransferToSameTarget(fromAccountId, toAccountId)) {
            riskScore += 15;
            details.append("VELOCITY_CHECK: Repeated transfer to same destination recently. ");
            log.warn("Fraud Rule 4 triggered: Repeated transfer to {} from {}", toAccountId, fromAccountId);
        }

        // Increment transfer counter in Redis (for rate limiting)
        incrementTransferCount(fromAccountId);
        trackTransferTarget(fromAccountId, toAccountId);

        // Determine risk level
        String riskLevel;
        boolean isFraud;
        if (riskScore >= 70) {
            riskLevel = "HIGH";
            isFraud = true;
        } else if (riskScore >= 40) {
            riskLevel = "MEDIUM";
            isFraud = true; // Block medium risk too for safety
        } else {
            riskLevel = "LOW";
            isFraud = false;
        }

        if (details.length() == 0) {
            details.append("No fraud indicators detected.");
        }

        log.info("Fraud analysis for txn {}: riskScore={}, riskLevel={}, isFraud={}",
                transactionId, riskScore, riskLevel, isFraud);

        return new FraudResult(riskScore, riskLevel, isFraud, details.toString());
    }

    // ==================== Redis-based Rate Limiting ====================

    private int getRecentTransferCount(String accountId) {
        String key = TRANSFER_COUNT_PREFIX + accountId;
        Object count = redisTemplate.opsForValue().get(key);
        return count != null ? Integer.parseInt(count.toString()) : 0;
    }

    private void incrementTransferCount(String accountId) {
        String key = TRANSFER_COUNT_PREFIX + accountId;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofHours(1));
    }

    private boolean hasRecentTransferToSameTarget(String fromAccountId, String toAccountId) {
        String key = "fraud:last-target:" + fromAccountId;
        Object lastTarget = redisTemplate.opsForValue().get(key);
        return toAccountId.equals(lastTarget != null ? lastTarget.toString() : null);
    }

    private void trackTransferTarget(String fromAccountId, String toAccountId) {
        String key = "fraud:last-target:" + fromAccountId;
        redisTemplate.opsForValue().set(key, toAccountId, Duration.ofMinutes(30));
    }

    // ==================== Result Record ====================

    public record FraudResult(int riskScore, String riskLevel, boolean isFraud, String details) {}
}
