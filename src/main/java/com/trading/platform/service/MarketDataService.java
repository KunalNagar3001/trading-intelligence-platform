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
import java.time.LocalDate;
import java.time.ZoneId;
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

    /**
     * Used by Stock DNA to auto-fill priceBefore/priceAfter for a canonical
     * moment when the caller only supplies an eventDate. Pulls ~10 days of
     * daily closes around the date from Yahoo's chart API (same endpoint
     * getQuote uses, just with a period1/period2 range instead of "now"),
     * then picks the last close strictly before eventDate and the first
     * close on/after eventDate.
     *
     * Returns empty if Yahoo has no data in that window (delisted symbol,
     * date too far in the past for Yahoo's free range, market holiday
     * gaps swallowing the whole window, etc.) — caller should fall back
     * to asking the user for manual prices in that case.
     */
    public Optional<HistoricalPriceWindow> getPricesAround(String symbol, LocalDate eventDate) {
        long period1 = eventDate.minusDays(7).atStartOfDay(ZoneId.of("Asia/Kolkata")).toEpochSecond();
        long period2 = eventDate.plusDays(7).atStartOfDay(ZoneId.of("Asia/Kolkata")).toEpochSecond();

        String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + symbol
                + "?period1=" + period1 + "&period2=" + period2 + "&interval=1d";

        try {
            String body = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
            JsonNode result = objectMapper.readTree(body).path("chart").path("result");
            if (!result.isArray() || result.isEmpty()) return Optional.empty();

            JsonNode timestamps = result.get(0).path("timestamp");
            JsonNode closes = result.get(0).path("indicators").path("quote").get(0).path("close");
            if (!timestamps.isArray() || !closes.isArray()) return Optional.empty();

            TreeMap<LocalDate, BigDecimal> closesByDate = new TreeMap<>();
            for (int i = 0; i < timestamps.size(); i++) {
                double close = closes.get(i).asDouble(Double.NaN);
                if (Double.isNaN(close)) continue;
                LocalDate date = java.time.Instant.ofEpochSecond(timestamps.get(i).asLong())
                        .atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();
                closesByDate.put(date, BigDecimal.valueOf(close).setScale(2, RoundingMode.HALF_UP));
            }

            Map.Entry<LocalDate, BigDecimal> before = closesByDate.headMap(eventDate, false).lastEntry();
            Map.Entry<LocalDate, BigDecimal> after = closesByDate.tailMap(eventDate, true).firstEntry();
            if (before == null || after == null) return Optional.empty();

            return Optional.of(new HistoricalPriceWindow(
                    before.getValue(), before.getKey(), after.getValue(), after.getKey()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public record HistoricalPriceWindow(BigDecimal priceBefore, LocalDate beforeDate,
                                        BigDecimal priceAfter, LocalDate afterDate) {}

}