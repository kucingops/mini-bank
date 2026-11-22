package com.minibank.transaction.listener;

import com.minibank.transaction.service.TransferService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Redis Stream Listener for transfer results.
 * Consumes events from transfer-validated and transfer-rejected streams.
 * 
 * This replaces Kafka Consumer — same consumer group pattern.
 * Uses polling-based approach with @Scheduled for simplicity; delivery is
 * at-least-once via {@link ReliableStreamConsumer}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransferResultListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final TransferService transferService;

    @Value("${redis.stream.transfer-validated}")
    private String transferValidatedStream;

    @Value("${redis.stream.transfer-rejected}")
    private String transferRejectedStream;

    @Value("${redis.stream.consumer-group}")
    private String consumerGroup;

    private ReliableStreamConsumer consumer;

    @PostConstruct
    void init() {
        consumer = new ReliableStreamConsumer(redisTemplate, consumerGroup,
                "transaction-service", Duration.ofSeconds(30), 5);
    }

    /**
     * Poll for new fraud-check results every 500ms.
     * Equivalent to Kafka consumer polling loop.
     */
    @Scheduled(fixedDelay = 500)
    public void consumeValidatedTransfers() {
        consumer.poll(transferValidatedStream, this::processValidatedTransfer);
    }

    @Scheduled(fixedDelay = 500)
    public void consumeRejectedTransfers() {
        consumer.poll(transferRejectedStream, this::processRejectedTransfer);
    }

    /**
     * Retry results whose processing failed or whose consumer died before acknowledging.
     * Safe because completeTransfer/rejectTransfer are idempotent.
     */
    @Scheduled(fixedDelay = 10_000)
    public void reclaimPending() {
        consumer.reclaim(transferValidatedStream, this::processValidatedTransfer);
        consumer.reclaim(transferRejectedStream, this::processRejectedTransfer);
    }

    private void processValidatedTransfer(MapRecord<String, Object, Object> record) {
        Map<Object, Object> data = record.getValue();
        String transactionId = data.get("transactionId").toString();

        log.info("Received TransferValidated event for transaction: {}", transactionId);
        transferService.completeTransfer(UUID.fromString(transactionId));
    }

    private void processRejectedTransfer(MapRecord<String, Object, Object> record) {
        Map<Object, Object> data = record.getValue();
        String transactionId = data.get("transactionId").toString();
        String reason = data.getOrDefault("reason", "Unknown").toString();

        log.warn("Received TransferRejected event for transaction: {}, reason: {}", transactionId, reason);
        transferService.rejectTransfer(UUID.fromString(transactionId));
    }
}
