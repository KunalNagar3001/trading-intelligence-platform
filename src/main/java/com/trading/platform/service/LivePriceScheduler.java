package com.trading.platform.service;

import com.trading.platform.kafka.producer.PriceEventProducer;
import com.trading.platform.repository.mysql.AlertRepository;
import com.trading.platform.repository.mysql.HoldingRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "app.market-data.mode", havingValue = "live")
public class LivePriceScheduler {

    private final MarketDataService marketDataService;
    private final PriceCacheService priceCacheService;
    private final PriceEventProducer priceEventProducer;
    private final AlertRepository alertRepository;
    private final HoldingRepository holdingRepository;

    public LivePriceScheduler(MarketDataService marketDataService,
                              PriceCacheService priceCacheService,
                              PriceEventProducer priceEventProducer,
                              AlertRepository alertRepository,
                              HoldingRepository holdingRepository) {
        this.marketDataService = marketDataService;
        this.priceCacheService = priceCacheService;
        this.priceEventProducer = priceEventProducer;
        this.alertRepository = alertRepository;
        this.holdingRepository = holdingRepository;
    }

    @Scheduled(fixedRate = 15000) // 15s — real quotes don't need 3s polling and this respects Yahoo's rate limits
    public void pollLivePrices() {
        Set<String> watched = getWatchedSymbols();
        if (watched.isEmpty()) return;

        Map<String, BigDecimal> quotes = marketDataService.getQuotes(List.copyOf(watched));

        quotes.forEach((symbol, price) -> {
            priceCacheService.put(symbol, price);
            priceEventProducer.sendPriceEvent(symbol, price.doubleValue());
        });
    }

    private Set<String> getWatchedSymbols() {
        Set<String> symbols = new HashSet<>(alertRepository.findDistinctActiveSymbols());
        symbols.addAll(holdingRepository.findDistinctSymbols());
        return symbols;
    }
}