package com.trading.platform.kafka.producer;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PriceEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private static final String TOPIC = "price-events";

    public PriceEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendPriceEvent(String symbol, double price) {
        String message = "{\"symbol\":\"" + symbol + "\",\"price\":" + price + "}";
        kafkaTemplate.send(TOPIC, symbol, message);
        System.out.println("Published to Kafka: " + message);
    }
}