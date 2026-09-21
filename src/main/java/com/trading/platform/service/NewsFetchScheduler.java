package com.trading.platform.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.news.mode", havingValue = "live")
public class NewsFetchScheduler {

    private final NewsAggregatorService newsAggregatorService;

    public NewsFetchScheduler(NewsAggregatorService newsAggregatorService) {
        this.newsAggregatorService = newsAggregatorService;
    }

    // Every 10 min — NewsAPI.org free tier allows 100 requests/day (~1 every
    // 14 min if run 24/7), 10 min leaves headroom for manual calls elsewhere.
    @Scheduled(fixedRate = 60000)
    public void fetchLatestNews() {
        newsAggregatorService.fetchAndProcessNews();
    }
}