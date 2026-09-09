package com.trading.platform.kafka.consumer;

import com.trading.platform.model.mysql.PriceAlert;
import com.trading.platform.service.AlertService;
import com.trading.platform.websocket.PriceWebSocketHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;

@Component
public class PriceAlertConsumer {

    private final PriceWebSocketHandler webSocketHandler;
    private final AlertService alertService;

    public PriceAlertConsumer(PriceWebSocketHandler webSocketHandler,
                              AlertService alertService) {
        this.webSocketHandler = webSocketHandler;
        this.alertService = alertService;
    }

    @KafkaListener(topics = "price-events", groupId = "trading-platform-group")
    public void consumePriceEvent(String message) {
        // Parse message
        String symbol = message.split("\"symbol\":\"")[1].split("\"")[0];
        double price = Double.parseDouble(message.split("\"price\":")[1].replace("}", ""));

        // Broadcast via WebSocket
        webSocketHandler.broadcastPrice(symbol, price);

        // Check price alerts
        List<PriceAlert> activeAlerts = alertService.getActiveAlertsForSymbol(symbol);
        BigDecimal currentPrice = BigDecimal.valueOf(price);

        for (PriceAlert alert : activeAlerts) {
            boolean triggered = false;

            if (alert.getCondition() == PriceAlert.AlertCondition.ABOVE
                    && currentPrice.compareTo(alert.getTargetPrice()) >= 0) {
                triggered = true;
                System.out.println("🚨 ALERT TRIGGERED: " + symbol
                        + " is ABOVE ₹" + alert.getTargetPrice()
                        + " (current: ₹" + price + ") — User ID: " + alert.getUserId());
            } else if (alert.getCondition() == PriceAlert.AlertCondition.BELOW
                    && currentPrice.compareTo(alert.getTargetPrice()) <= 0) {
                triggered = true;
                System.out.println("🚨 ALERT TRIGGERED: " + symbol
                        + " is BELOW ₹" + alert.getTargetPrice()
                        + " (current: ₹" + price + ") — User ID: " + alert.getUserId());
            }

            if (triggered) {
                alertService.deactivateAlert(alert);
                // In Phase 4, this would send an email/push notification
            }
        }
    }
}