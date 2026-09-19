package com.trading.platform.controller;

import com.trading.platform.model.mongo.NewsArticle;
import com.trading.platform.service.NewsAggregatorService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsAggregatorService newsAggregatorService;

    public NewsController(NewsAggregatorService newsAggregatorService) {
        this.newsAggregatorService = newsAggregatorService;
    }

    @GetMapping("/feed")
    public List<NewsArticle> getFeed(@RequestParam(defaultValue = "20") int limit) {
        return newsAggregatorService.getUnifiedFeed(limit);
    }

    @GetMapping("/{symbol}")
    public List<NewsArticle> getNewsForSymbol(@PathVariable String symbol,
                                              @RequestParam(defaultValue = "20") int limit) {
        return newsAggregatorService.getNewsForSymbol(symbol, limit);
    }
}