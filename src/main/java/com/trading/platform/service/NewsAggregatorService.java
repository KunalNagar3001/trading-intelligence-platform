package com.trading.platform.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.trading.platform.kafka.producer.NewsEventProducer;
import com.trading.platform.model.mongo.NewsArticle;
import com.trading.platform.model.mysql.Instrument;
import com.trading.platform.repository.mongo.NewsRepository;
import com.trading.platform.repository.mysql.InstrumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class NewsAggregatorService {

    // Headline/description keywords worth alerting a holder about — everything
    // else is still stored in the unified feed, just not treated as "urgent".
    // Same idea SentimentService will refine once it exists.
    private static final String[] IMPACT_KEYWORDS = {
            "crash", "plunge", "surge", "rally", "probe", "fraud", "scam",
            "circuit", "resignation", "verdict", "raid", "default",
            "downgrade", "upgrade", "scandal", "investigation", "ban", "recall"
    };

    private static final DateTimeFormatter NEWSAPI_DATE_FORMAT =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final InstrumentRepository instrumentRepository;
    private final NewsRepository newsRepository;
    private final NewsEventProducer newsEventProducer;

    @Value("${external-apis.news-api-key:}")
    private String newsApiKey;

    public NewsAggregatorService(ObjectMapper objectMapper,
                                 InstrumentRepository instrumentRepository,
                                 NewsRepository newsRepository,
                                 NewsEventProducer newsEventProducer) {
        this.objectMapper = objectMapper;
        this.instrumentRepository = instrumentRepository;
        this.newsRepository = newsRepository;
        this.newsEventProducer = newsEventProducer;
        this.restClient = RestClient.builder().build();
    }

    /**
     * Called by NewsFetchScheduler. Fetches recent market headlines, tags
     * each one against your active instruments, stores new ones, and
     * publishes a Kafka event for any that mention a held symbol.
     */
    public void fetchAndProcessNews() {
        if (newsApiKey == null || newsApiKey.isBlank()) {
            System.out.println("external-apis.news-api-key not configured — skipping news fetch");
            return;
        }

        List<Instrument> activeInstruments = instrumentRepository.findByActiveTrue();
        if (activeInstruments.isEmpty()) return;

        String query = URLEncoder.encode(
                "NSE OR BSE OR Sensex OR Nifty OR \"Indian stock market\"", StandardCharsets.UTF_8);
        String url = "https://newsapi.org/v2/everything?q=" + query
                + "&language=en&sortBy=publishedAt&pageSize=50&apiKey=" + newsApiKey;

        JsonNode articles;
        try {
            String body = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
            articles = objectMapper.readTree(body).path("articles");
        } catch (Exception e) {
            System.out.println("News fetch failed: " + e.getMessage());
            return;
        }

        int saved = 0;
        for (JsonNode a : articles) {
            String articleUrl = a.path("url").asText(null);
            if (articleUrl == null || newsRepository.existsByUrl(articleUrl)) continue;

            String headline = a.path("title").asText("");
            String description = a.path("description").asText("");

            List<String> symbols = tagSymbols(headline, description, activeInstruments);
            if (symbols.isEmpty()) continue; // not relevant to anything we track

            NewsArticle article = new NewsArticle();
            article.setHeadline(headline);
            article.setDescription(description);
            article.setSource(a.path("source").path("name").asText("Unknown"));
            article.setUrl(articleUrl);
            article.setTaggedSymbols(symbols);
            article.setPublishedAt(parsePublishedAt(a.path("publishedAt").asText(null)));

            newsRepository.save(article);
            saved++;

            if (isUrgent(headline, description)) {
                newsEventProducer.sendNewsEvent(article);
            }
        }

        System.out.println("News fetch complete: " + saved + " new articles saved out of "
                + articles.size() + " fetched");
    }

    public List<NewsArticle> getUnifiedFeed(int limit) {
        return newsRepository.findAllByOrderByPublishedAtDesc(
                org.springframework.data.domain.PageRequest.of(0, Math.min(limit, 100)));
    }

    public List<NewsArticle> getNewsForSymbol(String symbol, int limit) {
        return newsRepository.findByTaggedSymbolsContainingOrderByPublishedAtDesc(
                symbol.toUpperCase(), org.springframework.data.domain.PageRequest.of(0, Math.min(limit, 100)));
    }

    private List<String> tagSymbols(String headline, String description, List<Instrument> instruments) {
        String text = (headline + " " + description).toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (Instrument instrument : instruments) {
            if (text.contains(instrument.getSymbol().toLowerCase(Locale.ROOT))
                    || text.contains(instrument.getName().toLowerCase(Locale.ROOT))) {
                matches.add(instrument.getSymbol());
            }
        }
        return matches;
    }

    private boolean isUrgent(String headline, String description) {
        String text = (headline + " " + description).toLowerCase(Locale.ROOT);
        for (String keyword : IMPACT_KEYWORDS) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private LocalDateTime parsePublishedAt(String isoTimestamp) {
        if (isoTimestamp == null) return LocalDateTime.now();
        try {
            return Instant.parse(isoTimestamp).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}