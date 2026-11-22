package com.minibank.fraud.listener;

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
            RecordId id = message.getId();
            // XCLAIM bumps the delivery count, so the attempt is counted even if the
            // payload turns out to be unreadable below
            if (!claim(stream, id)) {
                continue; // claimed by another instance in the meantime, or trimmed
            }
            long deliveries = message.getTotalDeliveryCount() + 1;
            if (deliveries > maxDeliveries) {
                deadLetter(stream, id, deliveries - 1);
                continue;
            }

            List<MapRecord<String, Object, Object>> records;
            try {
                records = ops().range(stream, Range.closed(id.getValue(), id.getValue()));
            } catch (Exception e) {
                log.error("Cannot read pending record {} on '{}' (delivery #{}): {}",
                        id, stream, deliveries, e.getMessage());
                continue;
            }
            if (records == null || records.isEmpty()) {
                ops().acknowledge(stream, group, id); // entry was trimmed; nothing left to process
                continue;
            }
            log.info("Retrying record {} on '{}' (delivery #{})", id, stream, deliveries);
            process(stream, records.get(0), handler);
        }
    }

    /**
     * Transfers ownership of a pending record to this consumer. XCLAIM (without JUSTID) bumps
     * the delivery count. Raw bytes are returned, so nothing is deserialized here.
     */
    private boolean claim(String stream, RecordId id) {
        // Redisson's adapter sends getIdleTime() as XCLAIM's min-idle-time argument (and throws
        // if it is unset), while Lettuce sends minIdle() there and idle() as the IDLE option.
        // Setting both works for either client; on Lettuce it only means a failed record is
        // retried on the next reclaim pass instead of after a further minIdle.
        RedisStreamCommands.XClaimOptions options = RedisStreamCommands.XClaimOptions
                .minIdle(minIdle).ids(id).idle(minIdle);
        List<ByteRecord> claimed = redisTemplate.execute((RedisCallback<List<ByteRecord>>) connection ->
                connection.streamCommands().xClaim(bytes(stream), group, consumerName, options));
        return claimed != null && !claimed.isEmpty();
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
     * Copies the raw entry (no deserialization, so malformed payloads work too) to
     * {@code <stream>:dlq} and acknowledges it. The record must already be claimed.
     */
    private void deadLetter(String stream, RecordId id, long deliveries) {
        byte[] key = bytes(stream);
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            Map<byte[], byte[]> payload = new LinkedHashMap<>();
            List<ByteRecord> raw = connection.streamCommands().xRange(key,
                    Range.closed(id.getValue(), id.getValue()));
            if (raw != null && !raw.isEmpty()) {
                payload.putAll(raw.get(0).getValue());
            }
            payload.put(bytes("dlq.originalStream"), bytes(stream));
            payload.put(bytes("dlq.originalId"), bytes(id.getValue()));
            payload.put(bytes("dlq.deliveryCount"), bytes(String.valueOf(deliveries)));
            payload.put(bytes("dlq.deadLetteredAt"), bytes(LocalDateTime.now().toString()));

            connection.streamCommands().xAdd(StreamRecords.newRecord().in(bytes(stream + ":dlq")).ofMap(payload));
            connection.streamCommands().xAck(key, group, id);
            return null;
        });
        log.error("Record {} on '{}' failed {} times — moved to '{}:dlq'", id, stream, deliveries, stream);
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
