package com.trading.platform.repository.mongo;

import com.trading.platform.model.mongo.StockDna;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockDnaRepository extends MongoRepository<StockDna, String> {

    Optional<StockDna> findBySymbol(String symbol);
}