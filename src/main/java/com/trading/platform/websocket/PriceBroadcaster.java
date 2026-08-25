package com.trading.platform.websocket;

import com.trading.platform.kafka.producer.PriceEventProducer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Random;

@Component
@EnableScheduling
public class PriceBroadcaster {

    private final PriceEventProducer priceEventProducer;
    private final RedisTemplate<String, String> redisTemplate;
    private final Random random = new Random();

    private double reliancePrice = 2400.0;
    private double tcsPrice = 3500.0;

    public PriceBroadcaster(PriceEventProducer priceEventProducer,
                            RedisTemplate<String, String> redisTemplate) {
        this.priceEventProducer = priceEventProducer;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(fixedRate = 3000)
    public void broadcastPrices() {
        reliancePrice = reliancePrice + (random.nextDouble() - 0.5) * reliancePrice * 0.005;
        tcsPrice = tcsPrice + (random.nextDouble() - 0.5) * tcsPrice * 0.005;

        double roundedReliance = Math.round(reliancePrice * 100.0) / 100.0;
        double roundedTcs = Math.round(tcsPrice * 100.0) / 100.0;

        // Cache in Redis
        redisTemplate.opsForValue().set("price:RELIANCE", String.valueOf(roundedReliance));
        redisTemplate.opsForValue().set("price:TCS", String.valueOf(roundedTcs));

        // Publish to Kafka (consumer will handle WebSocket broadcast)
        priceEventProducer.sendPriceEvent("RELIANCE", roundedReliance);
        priceEventProducer.sendPriceEvent("TCS", roundedTcs);
    }
}