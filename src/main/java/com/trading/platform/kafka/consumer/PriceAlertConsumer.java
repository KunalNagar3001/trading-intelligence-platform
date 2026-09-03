package com.trading.platform.kafka.consumer;

import com.trading.platform.websocket.PriceWebSocketHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PriceAlertConsumer {

    private final PriceWebSocketHandler webSocketHandler;

    public PriceAlertConsumer(PriceWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @KafkaListener(topics = "price-events", groupId = "trading-platform-group")
    public void consumePriceEvent(String message) {
        System.out.println("Consumed from Kafka: " + message);

        // Parse the JSON message manually (no external JSON lib needed)
        String symbol = message.split("\"symbol\":\"")[1].split("\"")[0];
        double price = Double.parseDouble(message.split("\"price\":")[1].replace("}", ""));

        // Broadcast via WebSocket
        webSocketHandler.broadcastPrice(symbol, price);
    }
}