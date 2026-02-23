package com.minibank.transaction.listener;

import com.minibank.transaction.service.TransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Redis Stream Listener for transfer results.
 * Consumes events from transfer-validated and transfer-rejected streams.
 * 
 * This replaces Kafka Consumer — same consumer group pattern.
 * Uses polling-based approach with @Scheduled for simplicity.
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

    private static final String CONSUMER_NAME = "transaction-service-consumer";

    /**
     * Poll for validated transfers every 500ms.
     * Equivalent to Kafka consumer polling loop.
     */
    @Scheduled(fixedDelay = 500)
    public void consumeValidatedTransfers() {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                    Consumer.from(consumerGroup, CONSUMER_NAME),
                    StreamReadOptions.empty().count(10).block(Duration.ofMillis(100)),
                    StreamOffset.create(transferValidatedStream, ReadOffset.lastConsumed())
            );

            if (records != null && !records.isEmpty()) {
                for (MapRecord<String, Object, Object> record : records) {
                    processValidatedTransfer(record);
                    // Acknowledge message (like Kafka commit offset)
                    redisTemplate.opsForStream().acknowledge(consumerGroup, record);
                }
            }
        } catch (Exception e) {
            log.debug("Polling transfer-validated: {}", e.getMessage());
        }
    }

    /**
     * Poll for rejected transfers every 500ms.
     */
    @Scheduled(fixedDelay = 500)
    public void consumeRejectedTransfers() {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                    Consumer.from(consumerGroup, CONSUMER_NAME),
                    StreamReadOptions.empty().count(10).block(Duration.ofMillis(100)),
                    StreamOffset.create(transferRejectedStream, ReadOffset.lastConsumed())
            );

            if (records != null && !records.isEmpty()) {
                for (MapRecord<String, Object, Object> record : records) {
                    processRejectedTransfer(record);
                    redisTemplate.opsForStream().acknowledge(consumerGroup, record);
                }
            }
        } catch (Exception e) {
            log.debug("Polling transfer-rejected: {}", e.getMessage());
        }
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
