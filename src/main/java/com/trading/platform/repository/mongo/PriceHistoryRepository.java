package com.trading.platform.repository.mongo;

import com.trading.platform.model.mongo.PriceHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PriceHistoryRepository extends MongoRepository<PriceHistory, String> {

    List<PriceHistory> findBySymbol(String symbol);

    List<PriceHistory> findBySymbolOrderByTimestampDesc(String symbol, Pageable pageable);
}