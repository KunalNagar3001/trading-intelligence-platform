package com.trading.platform.dto.response;

import java.math.BigDecimal;

/**
 * P&L snapshot for a single holding, computed against the live market price.
 * priceStale is true when Yahoo had no quote for the symbol (rate-limited,
 * delisted, market closed with no cache, etc.) and we fell back to the buy
 * price instead — the client should show a "price unavailable" badge rather
 * than trust profitLoss as real in that case.
 */
public record HoldingPnl(
        Long holdingId,
        String symbol,
        String assetType,
        Integer quantity,
        BigDecimal buyPrice,
        BigDecimal currentPrice,
        boolean priceStale,
        BigDecimal investedValue,
        BigDecimal currentValue,
        BigDecimal profitLoss,
        BigDecimal profitLossPercent
) {}