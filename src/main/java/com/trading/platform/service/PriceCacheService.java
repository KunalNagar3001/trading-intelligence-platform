package com.trading.platform.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

@Service
public class PriceCacheService {

    private static final String KEY_PREFIX = "price:";
    private static final Duration TTL = Duration.ofMinutes(2); // stale price beyond this = don't trust the cache

    private final RedisTemplate<String, String> redisTemplate;

    public PriceCacheService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void put(String symbol, BigDecimal price) {
        redisTemplate.opsForValue().set(KEY_PREFIX + symbol, price.toPlainString(), TTL);
    }

    public Optional<BigDecimal> get(String symbol) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + symbol);
        if (value == null) return Optional.empty();
        try {
            return Optional.of(new BigDecimal(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}