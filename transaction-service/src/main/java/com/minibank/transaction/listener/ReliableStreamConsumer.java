package com.minibank.transaction.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * At-least-once Redis Stream consumer for one consumer group.
 *
 * - Each service instance gets its own consumer name, so several replicas can share a group.
 * - A record is acknowledged only after its handler succeeds. A failure is logged at ERROR
 *   and the record stays in the group's pending list instead of being silently dropped.
 * - {@link #reclaim} periodically claims records that have been pending longer than
 *   {@code minIdle} (failed, or owned by a crashed instance) and retries them.
 * - After {@code maxDeliveries} attempts a record is copied to {@code <stream>:dlq} and
 *   acknowledged, so one poison message cannot block the stream forever.
 *
 * Handlers must therefore be idempotent.
 */
@Slf4j
public class ReliableStreamConsumer {

    private static final int BATCH_SIZE = 10;

    private final RedisTemplate<String, Object> redisTemplate;
    private final String group;
    private final String consumerName;
    private final Duration minIdle;
    private final long maxDeliveries;

    public ReliableStreamConsumer(RedisTemplate<String, Object> redisTemplate, String group,
                                  String consumerPrefix, Duration minIdle, long maxDeliveries) {
        this.redisTemplate = redisTemplate;
        this.group = group;
        this.consumerName = consumerPrefix + "-" + instanceId();
        this.minIdle = minIdle;
        this.maxDeliveries = maxDeliveries;
        log.info("Stream consumer '{}' registered in group '{}'", consumerName, group);
    }

    public String getConsumerName() {
        return consumerName;
    }

    /**
     * Read new records for this group and process them.
     */
    public void poll(String stream, Consumer<MapRecord<String, Object, Object>> handler) {
        List<MapRecord<String, Object, Object>> records;
        try {
            records = ops().read(
                    org.springframework.data.redis.connection.stream.Consumer.from(group, consumerName),
                    StreamReadOptions.empty().count(BATCH_SIZE).block(Duration.ofMillis(100)),
                    StreamOffset.create(stream, ReadOffset.lastConsumed()));
        } catch (Exception e) {
            log.warn("Failed to read from stream '{}': {}", stream, e.getMessage());
            return;
        }
        if (records == null) {
            return;
        }
        records.forEach(record -> process(stream, record, handler));
    }

    /**
     * Retry records stuck in the pending list (handler failed or consumer died).
     */
    public void reclaim(String stream, Consumer<MapRecord<String, Object, Object>> handler) {
        PendingMessages pending;
        try {
            pending = ops().pending(stream, group, Range.unbounded(), BATCH_SIZE * 5L);
        } catch (Exception e) {
            log.warn("Failed to list pending records on '{}': {}", stream, e.getMessage());
            return;
        }

        for (PendingMessage message : pending) {
            if (message.getElapsedTimeSinceLastDelivery().compareTo(minIdle) < 0) {
                continue;
            }
            List<MapRecord<String, Object, Object>> claimed =
                    ops().claim(stream, group, consumerName, minIdle, message.getId());
            if (claimed.isEmpty()) {
                // Trimmed from the stream or claimed by another instance in the meantime
                continue;
            }
            MapRecord<String, Object, Object> record = claimed.get(0);
            if (message.getTotalDeliveryCount() >= maxDeliveries) {
                deadLetter(stream, record, message.getTotalDeliveryCount());
            } else {
                log.info("Retrying record {} on '{}' (delivery #{})",
                        record.getId(), stream, message.getTotalDeliveryCount() + 1);
                process(stream, record, handler);
            }
        }
    }

    private void process(String stream, MapRecord<String, Object, Object> record,
                         Consumer<MapRecord<String, Object, Object>> handler) {
        try {
            handler.accept(record);
            ops().acknowledge(stream, group, record.getId());
        } catch (Exception e) {
            // Not acknowledged: stays pending and is retried by reclaim()
            log.error("Failed to process record {} from '{}', will retry: {}",
                    record.getId(), stream, e.getMessage(), e);
        }
    }

    private void deadLetter(String stream, MapRecord<String, Object, Object> record, long deliveries) {
        Map<Object, Object> payload = new HashMap<>(record.getValue());
        payload.put("originalStream", stream);
        payload.put("originalId", record.getId().getValue());
        payload.put("deliveryCount", String.valueOf(deliveries));
        payload.put("deadLetteredAt", LocalDateTime.now().toString());

        ops().add(StreamRecords.newRecord().in(stream + ":dlq").ofMap(payload));
        ops().acknowledge(stream, group, record.getId());
        log.error("Record {} on '{}' failed {} times — moved to '{}:dlq'",
                record.getId(), stream, deliveries, stream);
    }

    private StreamOperations<String, Object, Object> ops() {
        return redisTemplate.opsForStream();
    }

    private static String instanceId() {
        String host = System.getenv("HOSTNAME");
        if (host == null || host.isBlank()) {
            try {
                host = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                host = UUID.randomUUID().toString().substring(0, 8);
            }
        }
        return host;
    }
}
