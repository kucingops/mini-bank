package com.minibank.fraud.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.RedisTemplate;

import jakarta.annotation.PostConstruct;

/**
 * Initialize Redis Streams consumer groups for the Fraud Service.
 */
@Configuration
@Slf4j
public class RedisStreamConfig {

    @Value("${redis.stream.transfer-requested}")
    private String transferRequestedStream;

    @Value("${redis.stream.consumer-group}")
    private String consumerGroup;

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisStreamConfig(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void initStreams() {
        createStreamGroupIfNotExists(transferRequestedStream, consumerGroup);
    }

    private void createStreamGroupIfNotExists(String streamKey, String group) {
        try {
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), group);
            log.info("Created consumer group '{}' for stream '{}'", group, streamKey);
        } catch (Exception e) {
            log.debug("Consumer group '{}' may already exist for stream '{}': {}", group, streamKey, e.getMessage());
        }
    }
}
