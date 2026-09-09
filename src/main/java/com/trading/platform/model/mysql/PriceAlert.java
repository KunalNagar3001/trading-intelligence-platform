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
    @Column(name="alert_condition", nullable = false)
    private AlertCondition condition;

    @Column(nullable = false)
    private BigDecimal targetPrice;

    private boolean active = true;

    public enum AlertCondition {
        ABOVE,  // notify when price goes ABOVE target
        BELOW   // notify when price goes BELOW target
    }
}