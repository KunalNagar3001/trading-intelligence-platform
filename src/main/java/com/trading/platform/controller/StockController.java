package com.trading.platform.controller;

import com.trading.platform.model.mongo.PriceHistory;
import com.trading.platform.service.PriceHistoryService;
import org.springframework.web.bind.annotation.*;
import com.trading.platform.dto.response.StockSearchResult;
import com.trading.platform.service.MarketDataService;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final PriceHistoryService priceHistoryService;
    private final MarketDataService marketDataService;

    public StockController(PriceHistoryService priceHistoryService, MarketDataService marketDataService) {
        this.priceHistoryService = priceHistoryService;
        this.marketDataService = marketDataService;
    }

    @GetMapping("/price/{symbol}")
    public List<PriceHistory> getPriceHistory(@PathVariable String symbol) {
        return priceHistoryService.getPriceHistory(symbol);
    }

    @GetMapping("/price/{symbol}/latest")
    public String getLatestPrice(@PathVariable String symbol) {
        return priceHistoryService.getLatestPrice(symbol);
    }

    @GetMapping("/search")
    public List<StockSearchResult> search(@RequestParam String q) {
        return marketDataService.search(q);
    }

    @GetMapping("/quote/{symbol}")
    public BigDecimal getQuote(@PathVariable String symbol) {
        return marketDataService.getQuote(symbol)
                .orElseThrow(() -> new IllegalArgumentException("No live price available for " + symbol));
    }
}