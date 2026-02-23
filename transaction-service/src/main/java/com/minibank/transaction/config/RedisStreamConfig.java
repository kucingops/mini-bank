package com.minibank.transaction.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import jakarta.annotation.PostConstruct;
import java.time.Duration;

/**
 * Configures Redis Streams for event-driven messaging.
 * Replaces Kafka for the mini project — same consumer group pattern.
 */
@Configuration
@Slf4j
public class RedisStreamConfig {

    @Value("${redis.stream.transfer-validated}")
    private String transferValidatedStream;

    @Value("${redis.stream.transfer-rejected}")
    private String transferRejectedStream;

    @Value("${redis.stream.consumer-group}")
    private String consumerGroup;

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisStreamConfig(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Initialize consumer groups for streams this service listens to.
     * Similar to Kafka consumer group initialization.
     */
    @PostConstruct
    public void initStreams() {
        createStreamGroupIfNotExists(transferValidatedStream, consumerGroup);
        createStreamGroupIfNotExists(transferRejectedStream, consumerGroup);
    }

    private void createStreamGroupIfNotExists(String streamKey, String group) {
        try {
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), group);
            log.info("Created consumer group '{}' for stream '{}'", group, streamKey);
        } catch (Exception e) {
            // Group may already exist — that's fine
            log.debug("Consumer group '{}' may already exist for stream '{}': {}", group, streamKey, e.getMessage());
        }
    }
}
