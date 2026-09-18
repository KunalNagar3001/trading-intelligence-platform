package com.trading.platform.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.trading.platform.dto.response.StockSearchResult;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class MarketDataService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public MarketDataService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36")
                .build();
    }

    public List<StockSearchResult> search(String query) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://query1.finance.yahoo.com/v1/finance/search?q=" + encoded
                + "&quotesCount=15&newsCount=0";

        try {
            String body = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
            JsonNode quotes = objectMapper.readTree(body).path("quotes");

            List<StockSearchResult> results = new ArrayList<>();
            for (JsonNode q : quotes) {
                String symbol = q.path("symbol").asText(null);
                String type = q.path("quoteType").asText("EQUITY");
                if (symbol == null) continue;
                if (!type.equals("EQUITY") && !type.equals("ETF")) continue; // skip options/futures/currencies

                String name = q.path("shortname").asText(q.path("longname").asText(symbol));
                String exchange = q.path("exchange").asText("");
                results.add(new StockSearchResult(symbol, name, exchange, type));
            }
            return results;
        } catch (Exception e) {
            throw new IllegalStateException("Could not search instruments right now. Try again shortly.");
        }
    }

    public Optional<BigDecimal> getQuote(String symbol) {
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + symbol;
        try {
            String body = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
            JsonNode result = objectMapper.readTree(body).path("chart").path("result");
            if (!result.isArray() || result.isEmpty()) return Optional.empty();

            double price = result.get(0).path("meta").path("regularMarketPrice").asDouble(Double.NaN);
            if (Double.isNaN(price)) return Optional.empty();

            return Optional.of(BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    public Map<String, BigDecimal> getQuotes(List<String> symbols) {
        Map<String, BigDecimal> results = new HashMap<>();
        for (String symbol : symbols) {
            getQuote(symbol).ifPresent(price -> results.put(symbol, price));
        }
        return results;
    }

}