package com.minibank.fraud.listener;

import com.minibank.fraud.document.AuditLog;
import com.minibank.fraud.service.AuditService;
import com.minibank.fraud.service.FraudDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Redis Stream Listener — consumes TransferRequested events.
 * Analyzes each transfer for fraud and publishes result events.
 * 
 * This is the consumer side of the event-driven architecture.
 * Equivalent to a Kafka consumer with consumer groups.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransferRequestListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final FraudDetectionService fraudDetectionService;
    private final AuditService auditService;

    @Value("${redis.stream.transfer-requested}")
    private String transferRequestedStream;

    @Value("${redis.stream.transfer-validated}")
    private String transferValidatedStream;

    @Value("${redis.stream.transfer-rejected}")
    private String transferRejectedStream;

    @Value("${redis.stream.consumer-group}")
    private String consumerGroup;

    private static final String CONSUMER_NAME = "fraud-service-consumer";

    /**
     * Poll for transfer-requested events every 500ms.
     * Process each event through fraud detection rules.
     */
    @Scheduled(fixedDelay = 500)
    public void consumeTransferRequests() {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                    Consumer.from(consumerGroup, CONSUMER_NAME),
                    StreamReadOptions.empty().count(10).block(Duration.ofMillis(100)),
                    StreamOffset.create(transferRequestedStream, ReadOffset.lastConsumed())
            );

            if (records != null && !records.isEmpty()) {
                for (MapRecord<String, Object, Object> record : records) {
                    processTransferRequest(record);
                    // Acknowledge the message
                    redisTemplate.opsForStream().acknowledge(consumerGroup, record);
                }
            }
        } catch (Exception e) {
            log.debug("Polling transfer-requested: {}", e.getMessage());
        }
    }

    private void processTransferRequest(MapRecord<String, Object, Object> record) {
        Map<Object, Object> rawData = record.getValue();

        // Convert to String map
        Map<String, String> eventData = Map.of(
                "transactionId", rawData.get("transactionId").toString(),
                "referenceNo", rawData.getOrDefault("referenceNo", "").toString(),
                "fromAccountId", rawData.get("fromAccountId").toString(),
                "toAccountId", rawData.get("toAccountId").toString(),
                "amount", rawData.get("amount").toString(),
                "description", rawData.getOrDefault("description", "").toString()
        );

        String transactionId = eventData.get("transactionId");
        log.info("Processing TransferRequested for transaction: {}", transactionId);

        // Run fraud analysis
        FraudDetectionService.FraudResult result = fraudDetectionService.analyze(eventData);

        // Index audit log to Elasticsearch
        AuditLog auditLog = AuditLog.builder()
                .transactionId(transactionId)
                .referenceNo(eventData.get("referenceNo"))
                .fromAccountId(eventData.get("fromAccountId"))
                .toAccountId(eventData.get("toAccountId"))
                .amount(new BigDecimal(eventData.get("amount")))
                .description(eventData.get("description"))
                .fraudCheckResult(result.isFraud() ? "FLAGGED" : "PASSED")
                .riskScore(result.riskScore())
                .riskLevel(result.riskLevel())
                .details(result.details())
                .eventType("TRANSFER_FRAUD_CHECK")
                .timestamp(LocalDateTime.now())
                .status(result.isFraud() ? "REJECTED" : "VALIDATED")
                .build();

        auditService.indexAuditLog(auditLog);

        // Publish result event to appropriate stream
        if (result.isFraud()) {
            publishRejection(transactionId, result);
        } else {
            publishValidation(transactionId, result);
        }
    }

    private void publishValidation(String transactionId, FraudDetectionService.FraudResult result) {
        Map<String, String> eventData = Map.of(
                "transactionId", transactionId,
                "riskScore", String.valueOf(result.riskScore()),
                "riskLevel", result.riskLevel(),
                "details", result.details(),
                "timestamp", LocalDateTime.now().toString()
        );

        redisTemplate.opsForStream()
                .add(StreamRecords.newRecord()
                        .in(transferValidatedStream)
                        .ofMap(eventData));

        log.info("Published TransferValidated for transaction: {}", transactionId);
    }

    private void publishRejection(String transactionId, FraudDetectionService.FraudResult result) {
        Map<String, String> eventData = Map.of(
                "transactionId", transactionId,
                "reason", result.details(),
                "riskScore", String.valueOf(result.riskScore()),
                "riskLevel", result.riskLevel(),
                "timestamp", LocalDateTime.now().toString()
        );

        redisTemplate.opsForStream()
                .add(StreamRecords.newRecord()
                        .in(transferRejectedStream)
                        .ofMap(eventData));

        log.warn("Published TransferRejected for transaction: {} (risk: {})", transactionId, result.riskLevel());
    }
}
