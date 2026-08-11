package com.trading.platform.model.mysql;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "holdings")
@Data
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String assetType; // EQUITY, GOLD_ETF, SILVER_ETF, OIL_ETF

    @Column(nullable = false)
    private BigDecimal buyPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private LocalDate buyDate;
}