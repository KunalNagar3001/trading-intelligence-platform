package com.trading.platform.model.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "news_articles")
@Data
public class NewsArticle {

    @Id
    private String id;

    private String headline;
    private String description;
    private String source;
    private String url;

    private List<String> taggedSymbols;

    // Populated once SentimentService exists — left null until then
    private String sentiment;      // POSITIVE / NEGATIVE / NEUTRAL
    private Double sentimentScore; // -1.0 to 1.0

    private LocalDateTime publishedAt;
    private LocalDateTime fetchedAt = LocalDateTime.now();
}