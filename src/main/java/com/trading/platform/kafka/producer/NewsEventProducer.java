package com.trading.platform.kafka.producer;

import tools.jackson.databind.ObjectMapper;
import com.trading.platform.model.mongo.NewsArticle;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class NewsEventProducer {

    private static final String TOPIC = "news-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public NewsEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    // Uses ObjectMapper rather than manual string concat (like PriceEventProducer does)
    // because headlines/descriptions can contain quotes and commas that would break
    // hand-built JSON — worth doing here even though the price producer doesn't need it.
    public void sendNewsEvent(NewsArticle article) {
        try {
            String message = objectMapper.writeValueAsString(article);
            kafkaTemplate.send(TOPIC, article.getTaggedSymbols().get(0), message);
            System.out.println("Published news event to Kafka: " + article.getHeadline());
        } catch (Exception e) {
            System.out.println("Failed to publish news event: " + e.getMessage());
        }
    }
}