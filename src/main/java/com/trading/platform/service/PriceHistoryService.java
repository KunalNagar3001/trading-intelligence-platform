package com.trading.platform.service;

import com.trading.platform.model.mongo.PriceHistory;
import com.trading.platform.repository.mongo.PriceHistoryRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.List;

@Service
public class PriceHistoryService {

    private final PriceHistoryRepository priceHistoryRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public PriceHistoryService(PriceHistoryRepository priceHistoryRepository, RedisTemplate<String, String> redisTemplate) {
        this.priceHistoryRepository = priceHistoryRepository;
        this.redisTemplate = redisTemplate;

    }
    public List<PriceHistory> getPriceHistory(String symbol) {
        return priceHistoryRepository.findBySymbol((symbol));
    }

    public String getLatestPrice(String symbol) {
        String price = redisTemplate.opsForValue().get("price:" + symbol.toUpperCase());
        return price != null ? price : "Price not available — no data cached yet";
    }
}