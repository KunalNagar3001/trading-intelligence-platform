package com.trading.platform.kafka.event;

import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * The payload of every message on the price-events topic.
 * <p>
 * eventId identifies one price observation, so consumers can recognise a duplicate
 * delivery and a single price can be traced through logs. timestamp (epoch millis) is
 * when the price was observed, so a consumer can tell how old an event is.
 */
public record PriceEvent(String eventId, String symbol, double price, long timestamp) {

    public static PriceEvent of(String symbol, double price) {
        return new PriceEvent(UUID.randomUUID().toString(), symbol, price, System.currentTimeMillis());
    }

    /**
     * Parses a message. A malformed message throws IllegalArgumentException, which the Kafka
     * error handler treats as "retrying cannot help" and sends straight to the dead-letter topic.
     */
    public static PriceEvent fromJson(ObjectMapper objectMapper, String json) {
        try {
            return objectMapper.readValue(json, PriceEvent.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed price event: " + json, e);
        }
    }
}
