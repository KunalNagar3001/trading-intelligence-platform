package com.trading.platform.kafka.consumer;

import com.trading.platform.config.KafkaTopicConfig;
import com.trading.platform.kafka.event.PriceEvent;
import com.trading.platform.model.mongo.PriceHistory;
import com.trading.platform.repository.mongo.PriceHistoryRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Stores every price event in MongoDB so /api/stocks/price/{symbol} has data to return.
 * <p>
 * It has its own consumer group, like the alert and WebSocket consumers, so a slow database write
 * cannot delay alerts or the live price stream. Each record is a point-in-time snapshot (only
 * {@code close} is filled in; open, high, low and volume stay null), not a candle.
 */
@Component
public class PriceHistoryConsumer {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final PriceHistoryRepository priceHistoryRepository;
    private final ObjectMapper objectMapper;

    public PriceHistoryConsumer(PriceHistoryRepository priceHistoryRepository, ObjectMapper objectMapper) {
        this.priceHistoryRepository = priceHistoryRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopicConfig.PRICE_EVENTS, groupId = KafkaTopicConfig.PRICE_HISTORY_GROUP)
    public void store(String message) {
        // A malformed message throws IllegalArgumentException and goes straight to the dead-letter topic
        PriceEvent event = PriceEvent.fromJson(objectMapper, message);

        PriceHistory snapshot = new PriceHistory();
        snapshot.setSymbol(event.symbol());
        snapshot.setClose(BigDecimal.valueOf(event.price()).setScale(2, RoundingMode.HALF_UP));
        snapshot.setTimestamp(LocalDateTime.ofInstant(Instant.ofEpochMilli(event.timestamp()), IST));
        priceHistoryRepository.save(snapshot);
    }
}
