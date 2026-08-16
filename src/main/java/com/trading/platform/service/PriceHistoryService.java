package com.trading.platform.service;

import com.trading.platform.model.mongo.PriceHistory;
import com.trading.platform.repository.mongo.PriceHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.List;

@Service
public class PriceHistoryService {

    private final PriceHistoryRepository priceHistoryRepository;

    public PriceHistoryService(PriceHistoryRepository priceHistoryRepository) {
        this.priceHistoryRepository = priceHistoryRepository;

    }
    public List<PriceHistory> getPriceHistory(String symbol) {
        return priceHistoryRepository.findBySymbol((symbol));
    }
}