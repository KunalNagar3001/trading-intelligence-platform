package com.trading.platform.model.mongo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * One defining historical event for a stock — embedded inside StockDna's
 * canonicalMoments list rather than its own collection, since moments are
 * always read/written as part of a symbol's full timeline.
 */
@Data
public class CanonicalMoment {

    private String id = UUID.randomUUID().toString();

    private LocalDate eventDate;
    private String eventDescription;   // e.g. "Hindenburg report published"
    private BigDecimal priceBefore;
    private BigDecimal priceAfter;
    private Double percentageImpact;   // auto-calculated from price before/after if not supplied
    private String source;             // optional URL / article reference

    private String addedBy;            // user's email, or "system" for seeded moments
    private LocalDateTime createdAt = LocalDateTime.now();
}