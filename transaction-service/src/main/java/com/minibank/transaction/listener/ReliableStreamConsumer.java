package com.minibank.transaction.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
 *   acknowledged, so one poison message cannot block the stream forever. This also
 *   covers payloads that cannot be deserialized at all.
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
            if (message.getTotalDeliveryCount() >= maxDeliveries) {
                deadLetter(stream, message);
                continue;
            }
            List<MapRecord<String, Object, Object>> claimed;
            try {
                claimed = ops().claim(stream, group, consumerName, minIdle, message.getId());
            } catch (Exception e) {
                // Claimed on the server but the payload cannot be deserialized. The delivery
                // count still went up, so it is dead-lettered once it reaches maxDeliveries.
                log.error("Cannot read pending record {} on '{}' (delivery #{}): {}",
                        message.getId(), stream, message.getTotalDeliveryCount() + 1, e.getMessage());
                continue;
            }
            if (claimed.isEmpty()) {
                // Trimmed from the stream or claimed by another instance in the meantime
                continue;
            }
            MapRecord<String, Object, Object> record = claimed.get(0);
            log.info("Retrying record {} on '{}' (delivery #{})",
                    record.getId(), stream, message.getTotalDeliveryCount() + 1);
            process(stream, record, handler);
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

    /**
     * Claims the record by ID only (no deserialization, so this works for malformed payloads),
     * copies the raw entry to {@code <stream>:dlq} and acknowledges it.
     */
    private void deadLetter(String stream, PendingMessage message) {
        RecordId id = message.getId();
        byte[] key = stream.getBytes(StandardCharsets.UTF_8);
        byte[] dlqKey = (stream + ":dlq").getBytes(StandardCharsets.UTF_8);

        Boolean moved = redisTemplate.execute((RedisCallback<Boolean>) connection -> {
            List<RecordId> claimed = connection.streamCommands().xClaimJustId(key, group, consumerName,
                    RedisStreamCommands.XClaimOptions.minIdle(minIdle).ids(id));
            if (claimed == null || claimed.isEmpty()) {
                return false; // claimed by another instance in the meantime
            }

            Map<byte[], byte[]> payload = new LinkedHashMap<>();
            List<ByteRecord> raw = connection.streamCommands().xRange(key,
                    Range.closed(id.getValue(), id.getValue()));
            if (raw != null && !raw.isEmpty()) {
                payload.putAll(raw.get(0).getValue());
            }
            payload.put(bytes("dlq.originalStream"), bytes(stream));
            payload.put(bytes("dlq.originalId"), bytes(id.getValue()));
            payload.put(bytes("dlq.deliveryCount"), bytes(String.valueOf(message.getTotalDeliveryCount())));
            payload.put(bytes("dlq.deadLetteredAt"), bytes(LocalDateTime.now().toString()));

            connection.streamCommands().xAdd(StreamRecords.newRecord().in(dlqKey).ofMap(payload));
            connection.streamCommands().xAck(key, group, id);
            return true;
        });

        if (Boolean.TRUE.equals(moved)) {
            log.error("Record {} on '{}' failed {} times — moved to '{}:dlq'",
                    id, stream, message.getTotalDeliveryCount(), stream);
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
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
