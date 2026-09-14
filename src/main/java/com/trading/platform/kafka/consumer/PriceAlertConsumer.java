package com.trading.platform.kafka.consumer;

import com.trading.platform.model.mysql.PriceAlert;
import com.trading.platform.service.AlertService;
import com.trading.platform.service.NotificationService;
import com.trading.platform.websocket.PriceWebSocketHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;

@Component
public class PriceAlertConsumer {

    private final PriceWebSocketHandler webSocketHandler;
    private final AlertService alertService;
    private final NotificationService notificationService;

    public PriceAlertConsumer(PriceWebSocketHandler webSocketHandler,
                              AlertService alertService,
                              NotificationService notificationService) {
        this.webSocketHandler = webSocketHandler;
        this.alertService = alertService;
        this.notificationService = notificationService;
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

        for (PriceAlert alert : activeAlerts) {
            if (isTriggered(alert, price)) {
                logTrigger(alert, symbol, price);
                alertService.deactivateAlert(alert);
                notificationService.sendAlertTriggeredEmail(alert, price);
            }
        }
    }

    private boolean isTriggered(PriceAlert alert, double price) {
        return switch (alert.getAlertType()) {
            case TARGET_PRICE, STOP_LOSS -> isTargetPriceTriggered(alert, price);
            case PERCENTAGE_CHANGE -> isPercentageChangeTriggered(alert, price);
        };
    }

    private boolean isTargetPriceTriggered(PriceAlert alert, double price) {
        BigDecimal currentPrice = BigDecimal.valueOf(price);
        BigDecimal target = alert.getTargetPrice();
        return alert.getCondition() == PriceAlert.AlertCondition.ABOVE
                ? currentPrice.compareTo(target) >= 0
                : currentPrice.compareTo(target) <= 0;
    }

    private boolean isPercentageChangeTriggered(PriceAlert alert, double price) {
        double basePrice = alert.getBasePrice().doubleValue();
        double percentChange = ((price - basePrice) / basePrice) * 100;
        return alert.getCondition() == PriceAlert.AlertCondition.ABOVE
                ? percentChange >= alert.getPercentageThreshold()
                : percentChange <= -alert.getPercentageThreshold();
    }

    private void logTrigger(PriceAlert alert, String symbol, double price) {
        switch (alert.getAlertType()) {
            case TARGET_PRICE -> System.out.println("🚨 ALERT TRIGGERED: " + symbol
                    + " is " + alert.getCondition() + " ₹" + alert.getTargetPrice()
                    + " (current: ₹" + price + ") — User ID: " + alert.getUserId());
            case STOP_LOSS -> System.out.println("🛑 STOP-LOSS TRIGGERED: " + symbol
                    + " fell to/below ₹" + alert.getTargetPrice()
                    + " (current: ₹" + price + ") — User ID: " + alert.getUserId());
            case PERCENTAGE_CHANGE -> {
                double basePrice = alert.getBasePrice().doubleValue();
                double percentChange = ((price - basePrice) / basePrice) * 100;
                System.out.printf("📈 PERCENTAGE ALERT TRIGGERED: %s moved %.2f%% from base ₹%s (current: ₹%.2f) — User ID: %d%n",
                        symbol, percentChange, alert.getBasePrice(), price, alert.getUserId());
            }
        }
    }
}