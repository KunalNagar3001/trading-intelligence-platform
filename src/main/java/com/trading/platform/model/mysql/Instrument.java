package com.trading.platform.model.mysql;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "instruments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol"}))
@Data
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 20)
    private String isin;

    @Column(nullable = false, length = 10)
    private String exchange; // NSE, BSE

    @Column(nullable = false, length = 10)
    private String assetType; // EQUITY, GOLD_ETF, SILVER_ETF, OIL_ETF — matches Holding.assetType

    @Column(nullable = false)
    private boolean active = true;
}