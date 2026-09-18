package com.trading.platform.websocket;

import com.trading.platform.kafka.producer.PriceEventProducer;
import com.trading.platform.repository.mysql.AlertRepository;
import com.trading.platform.repository.mysql.HoldingRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.stream.Collectors;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.market-data.mode", havingValue = "simulated", matchIfMissing = true)
public class PriceBroadcaster {

    private final PriceEventProducer priceEventProducer;
    private final RedisTemplate<String, String> redisTemplate;
    private final AlertRepository alertRepository;
    private final HoldingRepository holdingRepository;
    private final Random random = new Random();

    // symbol -> simulated price, grows/shrinks as symbols enter/leave use
    private final Map<String, Double> prices = new ConcurrentHashMap<>();

    public PriceBroadcaster(PriceEventProducer priceEventProducer,
                            RedisTemplate<String, String> redisTemplate,
                            AlertRepository alertRepository,
                            HoldingRepository holdingRepository) {
        this.priceEventProducer = priceEventProducer;
        this.redisTemplate = redisTemplate;
        this.alertRepository = alertRepository;
        this.holdingRepository = holdingRepository;
    }

    @Scheduled(fixedRate = 3000)
    public void broadcastPrices() {
        Set<String> watched = getWatchedSymbols();

        // drop symbols nobody cares about anymore
        prices.keySet().retainAll(watched);

        for (String symbol : watched) {
            double current = prices.computeIfAbsent(symbol, s -> seedPrice());
            double updated = current + (random.nextDouble() - 0.5) * current * 0.005;
            double rounded = Math.round(updated * 100.0) / 100.0;
            prices.put(symbol, rounded);

            redisTemplate.opsForValue().set("price:" + symbol, String.valueOf(rounded));
            priceEventProducer.sendPriceEvent(symbol, rounded);
        }
    }

    private Set<String> getWatchedSymbols() {
        Set<String> symbols = new java.util.HashSet<>(alertRepository.findDistinctActiveSymbols());
        symbols.addAll(holdingRepository.findDistinctSymbols());
        return symbols;
    }

    private double seedPrice() {
        // starting point for a symbol we haven't priced before;
        // replace with a real lookup once a market-data source is wired in
        return 100 + random.nextDouble() * 2900;
    }
}