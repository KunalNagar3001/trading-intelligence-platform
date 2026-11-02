package com.trading.platform.kafka.producer;

import com.trading.platform.config.KafkaTopicConfig;
import com.trading.platform.kafka.event.PriceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class PriceEventProducer {

    private static final Logger log = LoggerFactory.getLogger(PriceEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PriceEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendPriceEvent(String symbol, double price) {
        try {
            PriceEvent event = PriceEvent.of(symbol, price);
            String message = objectMapper.writeValueAsString(event);

            // The symbol is the message key: every event for one symbol goes to the same partition,
            // so a consumer sees that symbol's prices in the order they were published.
            kafkaTemplate.send(KafkaTopicConfig.PRICE_EVENTS, symbol, message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish price event {} for {}: {}", event.eventId(), symbol, ex.getMessage());
                        } else {
                            log.debug("Published price event {} for {} to partition {} at offset {}",
                                    event.eventId(), symbol,
                                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            // Never let one bad symbol abort the whole polling cycle
            log.error("Could not build or send price event for {}: {}", symbol, e.getMessage());
        }
    }
}
