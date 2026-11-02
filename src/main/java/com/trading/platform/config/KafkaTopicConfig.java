package com.trading.platform.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the topics instead of relying on the broker to auto-create them with one partition.
 * KafkaAdmin creates any that are missing at start-up (and adds partitions to an existing topic
 * that has fewer than declared).
 */
@Configuration
public class KafkaTopicConfig {

    public static final String PRICE_EVENTS = "price-events";
    public static final String NEWS_EVENTS = "news-events";
    public static final String DEAD_LETTER_SUFFIX = ".DLT";

    // Each independent consumer of price-events gets its own group, so each one receives every event.
    public static final String PRICE_WEBSOCKET_GROUP = "price-websocket-group";
    public static final String PRICE_ALERT_GROUP = "price-alert-group";

    // Price events are keyed by symbol, so all events for one symbol land on the same partition
    // (and stay in order) while different symbols can be spread across partitions.
    private static final int PRICE_PARTITIONS = 3;

    @Bean
    public NewTopic priceEventsTopic() {
        return TopicBuilder.name(PRICE_EVENTS).partitions(PRICE_PARTITIONS).replicas(1).build();
    }

    // The dead-letter topic needs at least as many partitions as the topic it receives from,
    // because a failed record is republished to the same partition number.
    @Bean
    public NewTopic priceEventsDeadLetterTopic() {
        return TopicBuilder.name(PRICE_EVENTS + DEAD_LETTER_SUFFIX).partitions(PRICE_PARTITIONS).replicas(1).build();
    }

    @Bean
    public NewTopic newsEventsTopic() {
        return TopicBuilder.name(NEWS_EVENTS).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic newsEventsDeadLetterTopic() {
        return TopicBuilder.name(NEWS_EVENTS + DEAD_LETTER_SUFFIX).partitions(1).replicas(1).build();
    }
}
