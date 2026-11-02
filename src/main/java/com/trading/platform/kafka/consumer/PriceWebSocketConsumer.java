package com.trading.platform.kafka.consumer;

import com.trading.platform.config.KafkaTopicConfig;
import com.trading.platform.kafka.event.PriceEvent;
import com.trading.platform.websocket.PriceWebSocketHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Pushes every price event to the connected WebSocket clients.
 * <p>
 * It has its own consumer group, so it receives every price event independently of the alert
 * consumer: a slow email or an alert failure can no longer delay the live price stream.
 */
@Component
public class PriceWebSocketConsumer {

    private final PriceWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

    public PriceWebSocketConsumer(PriceWebSocketHandler webSocketHandler, ObjectMapper objectMapper) {
        this.webSocketHandler = webSocketHandler;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopicConfig.PRICE_EVENTS, groupId = KafkaTopicConfig.PRICE_WEBSOCKET_GROUP)
    public void pushToClients(String message) {
        PriceEvent event = PriceEvent.fromJson(objectMapper, message);
        webSocketHandler.broadcastPrice(event.symbol(), event.price());
    }
}
