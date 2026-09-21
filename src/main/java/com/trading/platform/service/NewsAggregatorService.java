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

    // Minimum |sentimentScore| for a tagged article to trigger a Kafka event /
    // email — everything else still lands in the unified feed either way.
    private static final double URGENT_SCORE_THRESHOLD = 0.5;

    private static final DateTimeFormatter NEWSAPI_DATE_FORMAT =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final InstrumentRepository instrumentRepository;
    private final NewsRepository newsRepository;
    private final NewsEventProducer newsEventProducer;
    private final SentimentService sentimentService;

    @Value("${external-apis.news-api-key:}")
    private String newsApiKey;

    public NewsAggregatorService(ObjectMapper objectMapper,
                                 InstrumentRepository instrumentRepository,
                                 NewsRepository newsRepository,
                                 NewsEventProducer newsEventProducer,
                                 SentimentService sentimentService) {
        this.objectMapper = objectMapper;
        this.instrumentRepository = instrumentRepository;
        this.newsRepository = newsRepository;
        this.newsEventProducer = newsEventProducer;
        this.sentimentService = sentimentService;
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
        if (activeInstruments.isEmpty()) {
            System.out.println("No active instruments found — nothing to tag news against, skipping fetch");
            return;
        }

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

            SentimentResult sentiment = sentimentService.analyze(headline + " " + description);

            NewsArticle article = new NewsArticle();
            article.setHeadline(headline);
            article.setDescription(description);
            article.setSource(a.path("source").path("name").asText("Unknown"));
            article.setUrl(articleUrl);
            article.setTaggedSymbols(symbols);
            article.setSentiment(sentiment.label());
            article.setSentimentScore(sentiment.score());
            article.setPublishedAt(parsePublishedAt(a.path("publishedAt").asText(null)));

            newsRepository.save(article);
            saved++;

            // Urgent = strong sentiment in either direction, not just any tagged
            // article — a mildly worded piece about a held stock isn't worth an email.
            if (Math.abs(sentiment.score()) >= URGENT_SCORE_THRESHOLD) {
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

    private LocalDateTime parsePublishedAt(String isoTimestamp) {
        if (isoTimestamp == null) return LocalDateTime.now();
        try {
            return Instant.parse(isoTimestamp).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}