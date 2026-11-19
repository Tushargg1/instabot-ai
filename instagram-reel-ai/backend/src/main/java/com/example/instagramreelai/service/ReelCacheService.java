package com.example.instagramreelai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReelCacheService {
    private final StringRedisTemplate redis;

    @Value("${app.cache.reel-ttl:PT24H}")
    private Duration ttl;

    public Optional<UUID> getReelId(String canonicalKey) {
        try {
            String value = redis.opsForValue().get(reelKey(canonicalKey));
            return value == null ? Optional.empty() : Optional.of(UUID.fromString(value));
        } catch (RuntimeException unavailable) {
            log.debug("Redis unavailable; falling back to PostgreSQL");
            return Optional.empty();
        }
    }

    public void putReelId(String canonicalKey, UUID reelId) {
        try {
            redis.opsForValue().set(reelKey(canonicalKey), reelId.toString(), ttl);
        } catch (RuntimeException unavailable) {
            log.debug("Redis unavailable; canonical Reel remains durable in PostgreSQL");
        }
    }

    public Optional<String> getReply(UUID reelId) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(replyKey(reelId)));
        } catch (RuntimeException unavailable) {
            return Optional.empty();
        }
    }

    public void putReply(UUID reelId, String reply) {
        try {
            redis.opsForValue().set(replyKey(reelId), reply, ttl);
        } catch (RuntimeException unavailable) {
            log.debug("Redis unavailable; generated result remains durable in PostgreSQL");
        }
    }

    private String reelKey(String canonicalKey) {
        return "reel:canonical:" + canonicalKey;
    }

    private String replyKey(UUID reelId) {
        return "reel:reply:" + reelId;
    }
}
