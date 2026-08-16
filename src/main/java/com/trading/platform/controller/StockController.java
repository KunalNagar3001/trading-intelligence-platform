package com.trading.platform.controller;

import com.trading.platform.model.mongo.PriceHistory;
import com.trading.platform.service.PriceHistoryService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private final PriceHistoryService priceHistoryService;

    public StockController(PriceHistoryService priceHistoryService) {
        this.priceHistoryService = priceHistoryService;
    }

    @GetMapping("/price/{symbol}")
    public List<PriceHistory> getPriceHistory(@PathVariable String symbol) {
        return priceHistoryService.getPriceHistory(symbol);
    }
}