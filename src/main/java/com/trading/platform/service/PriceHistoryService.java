package com.trading.platform.service;

import com.trading.platform.model.mongo.PriceHistory;
import com.trading.platform.repository.mongo.PriceHistoryRepository;
import org.springframework.data.domain.PageRequest;
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
    /** Newest snapshots first. The limit is kept between 1 and 500 so one call cannot pull the whole collection. */
    public List<PriceHistory> getPriceHistory(String symbol, int limit) {
        int size = Math.max(1, Math.min(limit, 500));
        return priceHistoryRepository.findBySymbolOrderByTimestampDesc(
                symbol.toUpperCase(), PageRequest.of(0, size));
    }

    public String getLatestPrice(String symbol) {
        String price = redisTemplate.opsForValue().get("price:" + symbol.toUpperCase());
        return price != null ? price : "Price not available — no data cached yet";
    }
}