package com.trading.platform.kafka.consumer;

import com.trading.platform.config.KafkaTopicConfig;
import com.trading.platform.kafka.event.PriceEvent;
import com.trading.platform.model.mysql.PriceAlert;
import com.trading.platform.service.AlertService;
import com.trading.platform.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;

@Component
public class PriceAlertConsumer {

    private final AlertService alertService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public PriceAlertConsumer(AlertService alertService,
                              NotificationService notificationService,
                              ObjectMapper objectMapper) {
        this.alertService = alertService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    // Own consumer group: this listener only evaluates alerts. The WebSocket push is done by
    // PriceWebSocketConsumer, which reads the same topic independently.
    @KafkaListener(topics = KafkaTopicConfig.PRICE_EVENTS, groupId = KafkaTopicConfig.PRICE_ALERT_GROUP)
    public void consumePriceEvent(String message) {
        PriceEvent event = PriceEvent.fromJson(objectMapper, message);
        String symbol = event.symbol();
        double price = event.price();

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