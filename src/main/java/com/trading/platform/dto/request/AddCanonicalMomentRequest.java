package com.trading.platform.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AddCanonicalMomentRequest {
    private LocalDate eventDate;
    private String eventDescription;
    private BigDecimal priceBefore;
    private BigDecimal priceAfter;
    private String source;              // optional
    private Double percentageImpact;    // optional — auto-calculated from priceBefore/priceAfter if omitted
}