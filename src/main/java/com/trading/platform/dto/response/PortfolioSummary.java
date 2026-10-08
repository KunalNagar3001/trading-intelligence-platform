package com.trading.platform.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Full portfolio valuation: per-holding P&L plus totals and an asset
 * allocation breakdown. assetAllocationPercent is keyed by Holding.assetType
 * (EQUITY, GOLD_ETF, SILVER_ETF, OIL_ETF) and is a % of *current market
 * value*, not invested cost — "what % of my money is in equities right now"
 * is what a trader actually wants from an allocation breakdown.
 */
public record PortfolioSummary(
        List<HoldingPnl> holdings,
        BigDecimal totalInvested,
        BigDecimal totalCurrentValue,
        BigDecimal totalProfitLoss,
        BigDecimal totalProfitLossPercent,
        Map<String, BigDecimal> assetAllocationPercent
) {}