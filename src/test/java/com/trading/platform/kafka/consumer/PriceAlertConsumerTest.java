package com.trading.platform.kafka.consumer;

import com.trading.platform.model.mysql.PriceAlert;
import com.trading.platform.model.mysql.PriceAlert.AlertCondition;
import com.trading.platform.model.mysql.PriceAlert.AlertType;
import com.trading.platform.service.AlertService;
import com.trading.platform.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PriceAlertConsumerTest {

    private static final String SYMBOL = "INFY.NS";

    private AlertService alertService;
    private NotificationService notificationService;
    private PriceAlertConsumer consumer;

    @BeforeEach
    void setUp() {
        alertService = mock(AlertService.class);
        notificationService = mock(NotificationService.class);
        consumer = new PriceAlertConsumer(alertService, notificationService, JsonMapper.builder().build());
    }

    // ---- TARGET_PRICE / STOP_LOSS ----

    @ParameterizedTest(name = "{0} target {1}, price {2} -> triggered={3}")
    @CsvSource({
            "ABOVE, 1500.00, 1500.0, true",   // boundary is inclusive
            "ABOVE, 1500.00, 1500.5, true",
            "ABOVE, 1500.00, 1499.9, false",
            "BELOW, 1500.00, 1500.0, true",   // boundary is inclusive
            "BELOW, 1500.00, 1499.0, true",
            "BELOW, 1500.00, 1500.1, false"
    })
    void targetPriceAlertTriggersOnCorrectSide(AlertCondition condition, String target, double price, boolean triggered) {
        PriceAlert alert = targetAlert(AlertType.TARGET_PRICE, condition, target);
        when(alertService.getActiveAlertsForSymbol(SYMBOL)).thenReturn(List.of(alert));

        consumer.consumePriceEvent(event(SYMBOL, price));

        assertTriggered(alert, price, triggered);
    }

    @Test
    void stopLossTriggersWhenPriceFallsToTarget() {
        PriceAlert alert = targetAlert(AlertType.STOP_LOSS, AlertCondition.BELOW, "1400.00");
        when(alertService.getActiveAlertsForSymbol(SYMBOL)).thenReturn(List.of(alert));

        consumer.consumePriceEvent(event(SYMBOL, 1399.0));

        assertTriggered(alert, 1399.0, true);
    }

    // ---- PERCENTAGE_CHANGE ----

    @Test
    void percentageAlertAboveTriggersAfterRiseBeyondThreshold() {
        PriceAlert alert = percentAlert(AlertCondition.ABOVE, "100.00", 5.0);
        when(alertService.getActiveAlertsForSymbol(SYMBOL)).thenReturn(List.of(alert));

        consumer.consumePriceEvent(event(SYMBOL, 106.0)); // +6%

        assertTriggered(alert, 106.0, true);
    }

    @Test
    void percentageAlertAboveDoesNotTriggerBelowThreshold() {
        PriceAlert alert = percentAlert(AlertCondition.ABOVE, "100.00", 5.0);
        when(alertService.getActiveAlertsForSymbol(SYMBOL)).thenReturn(List.of(alert));

        consumer.consumePriceEvent(event(SYMBOL, 104.0)); // +4%

        assertTriggered(alert, 104.0, false);
    }

    @Test
    void percentageAlertBelowTriggersAfterFallBeyondThreshold() {
        PriceAlert alert = percentAlert(AlertCondition.BELOW, "200.00", 10.0);
        when(alertService.getActiveAlertsForSymbol(SYMBOL)).thenReturn(List.of(alert));

        consumer.consumePriceEvent(event(SYMBOL, 179.0)); // -10.5%

        assertTriggered(alert, 179.0, true);
    }

    @Test
    void percentageAlertBelowIgnoresRises() {
        PriceAlert alert = percentAlert(AlertCondition.BELOW, "200.00", 10.0);
        when(alertService.getActiveAlertsForSymbol(SYMBOL)).thenReturn(List.of(alert));

        consumer.consumePriceEvent(event(SYMBOL, 250.0)); // +25%

        assertTriggered(alert, 250.0, false);
    }

    // ---- behaviour around triggering ----

    @Test
    void onlyTheMatchingAlertsFire() {
        PriceAlert hit = targetAlert(AlertType.TARGET_PRICE, AlertCondition.ABOVE, "1500.00");
        PriceAlert miss = targetAlert(AlertType.TARGET_PRICE, AlertCondition.ABOVE, "1800.00");
        when(alertService.getActiveAlertsForSymbol(SYMBOL)).thenReturn(List.of(hit, miss));

        consumer.consumePriceEvent(event(SYMBOL, 1600.0));

        verify(alertService).deactivateAlert(hit);
        verify(alertService, never()).deactivateAlert(miss);
        verify(notificationService).sendAlertTriggeredEmail(hit, 1600.0);
        verify(notificationService, never()).sendAlertTriggeredEmail(eq(miss), anyDouble());
    }

    @Test
    void noActiveAlertsMeansNothingHappens() {
        when(alertService.getActiveAlertsForSymbol(SYMBOL)).thenReturn(List.of());

        consumer.consumePriceEvent(event(SYMBOL, 1500.0));

        verifyNoInteractions(notificationService);
        verify(alertService, never()).deactivateAlert(any());
    }

    @Test
    void malformedMessageThrowsIllegalArgumentSoItGoesToDeadLetterTopic() {
        assertThrows(IllegalArgumentException.class, () -> consumer.consumePriceEvent("not-json"));
        verifyNoInteractions(notificationService);
    }

    // ---- helpers ----

    private void assertTriggered(PriceAlert alert, double price, boolean triggered) {
        if (triggered) {
            verify(alertService).deactivateAlert(alert);
            verify(notificationService).sendAlertTriggeredEmail(alert, price);
        } else {
            verify(alertService, never()).deactivateAlert(any());
            verifyNoInteractions(notificationService);
        }
    }

    private static String event(String symbol, double price) {
        return "{\"eventId\":\"e-1\",\"symbol\":\"" + symbol + "\",\"price\":" + price
                + ",\"timestamp\":1700000000000}";
    }

    private static PriceAlert targetAlert(AlertType type, AlertCondition condition, String target) {
        PriceAlert a = new PriceAlert();
        a.setUserId(1L);
        a.setSymbol(SYMBOL);
        a.setAlertType(type);
        a.setCondition(condition);
        a.setTargetPrice(new BigDecimal(target));
        return a;
    }

    private static PriceAlert percentAlert(AlertCondition condition, String basePrice, double threshold) {
        PriceAlert a = new PriceAlert();
        a.setUserId(1L);
        a.setSymbol(SYMBOL);
        a.setAlertType(AlertType.PERCENTAGE_CHANGE);
        a.setCondition(condition);
        a.setBasePrice(new BigDecimal(basePrice));
        a.setPercentageThreshold(threshold);
        return a;
    }
}
