package com.trading.platform.model.mysql;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Table(name = "price_alerts")
@Data
public class PriceAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false)
    private AlertType alertType = AlertType.TARGET_PRICE;

    @Enumerated(EnumType.STRING)
    @Column(name="alert_condition", nullable = false)
    private AlertCondition condition;

    // Used by TARGET_PRICE and STOP_LOSS alerts
    private BigDecimal targetPrice;

    // Used by PERCENTAGE_CHANGE alerts: the % move required to trigger (e.g. 5 = 5%)
    private Double percentageThreshold;

    // Used by PERCENTAGE_CHANGE alerts: the price captured at creation time, used as the reference point
    private BigDecimal basePrice;

    private boolean active = true;

    public enum AlertCondition {
        ABOVE,  // notify when price goes ABOVE target / rises by percentageThreshold%
        BELOW   // notify when price goes BELOW target / falls by percentageThreshold%
    }

    public enum AlertType {
        TARGET_PRICE,     // fires once when price crosses targetPrice in the given direction
        STOP_LOSS,        // same mechanic as TARGET_PRICE, always BELOW — protects an open position
        PERCENTAGE_CHANGE // fires when price moves percentageThreshold% from basePrice, in the given direction
    }
}