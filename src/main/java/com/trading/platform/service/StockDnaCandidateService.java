package com.trading.platform.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.trading.platform.dto.response.NewsCandidate;
import com.trading.platform.model.mysql.Instrument;
import com.trading.platform.repository.mysql.InstrumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds candidate historical "canonical moments" for a stock via GDELT's
 * free DOC 2.0 API (no key needed, coverage back to ~2017).
 *
 * GDELT's sort=hybridrel ranking is heavily recency-biased within a single
 * request — a naive one-shot query spanning multiple years returns almost
 * entirely recent articles, since older ones lose out on relevance/engagement
 * signals GDELT uses. So instead of one big query, this splits the lookback
 * into two windows — RECENT (last 12 months) and OLDER (12 months back to
 * yearsBack years back) — queried separately and merged, so genuinely old
 * events (like a multi-year-old scandal or crash) actually have a chance to
 * show up alongside current news instead of being crowded out.
 *
 * Cost of this: two sequential GDELT calls instead of one. GDELT's free tier
 * only allows about 1 request per 5 seconds, so this endpoint deliberately
 * blocks for ~5.5s between the two calls — meaning a single /candidates
 * request now takes roughly 6-10 seconds to respond. That's an acceptable
 * trade for actually getting both time ranges rather than a fast response
 * that silently only covers the last few months.
 */
@Service
public class StockDnaCandidateService {

    private static final String BASE_URL = "https://api.gdeltproject.org/api/v2/doc/doc";

    // Keywords that tend to co-occur with genuinely canonical, price-moving events
    // rather than routine coverage — keeps results relevant instead of noisy.
    private static final String IMPACT_KEYWORDS =
            "(crash OR plunge OR surge OR rally OR probe OR fraud OR scam OR " +
                    "circuit OR resignation OR verdict OR raid OR default OR downgrade OR " +
                    "upgrade OR scandal OR investigation OR ban OR recall)";

    private static final DateTimeFormatter GDELT_REQUEST_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter GDELT_RESPONSE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    // GDELT's stated limit is ~1 request per 5 seconds — pad slightly for safety.
    private static final long GDELT_PACING_MS = 5500;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final InstrumentRepository instrumentRepository;

    public StockDnaCandidateService(ObjectMapper objectMapper, InstrumentRepository instrumentRepository) {
        this.objectMapper = objectMapper;
        this.instrumentRepository = instrumentRepository;
        this.restClient = RestClient.builder().build();
    }

    public List<NewsCandidate> findCandidates(String symbol, int yearsBack) {
        Instrument instrument = instrumentRepository.findBySymbolAndActiveTrue(symbol.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Unknown or inactive symbol: " + symbol));

        String query = "\"" + instrument.getName() + "\" " + IMPACT_KEYWORDS;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneYearAgo = now.minusYears(1);

        // Single window if yearsBack <= 1 — nothing to split.
        if (yearsBack <= 1) {
            return searchWindow(query, now.minusYears(yearsBack), now, symbol);
        }

        List<NewsCandidate> recent = searchWindow(query, oneYearAgo, now, symbol);
        sleepForRateLimit();
        List<NewsCandidate> older = searchWindow(query, now.minusYears(yearsBack), oneYearAgo, symbol);

        return mergeDeduped(recent, older);
    }

    private List<NewsCandidate> searchWindow(String query, LocalDateTime start, LocalDateTime end, String symbol) {
        String url = BASE_URL
                + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&mode=artlist&format=json&maxrecords=125"
                + "&startdatetime=" + start.format(GDELT_REQUEST_FORMAT)
                + "&enddatetime=" + end.format(GDELT_REQUEST_FORMAT)
                + "&sort=hybridrel";

        JsonNode articles;
        try {
            String body = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
            articles = objectMapper.readTree(body).path("articles");
        } catch (Exception e) {
            System.out.println("GDELT candidate search failed for " + symbol
                    + " (window " + start.toLocalDate() + " to " + end.toLocalDate() + "): " + e.getMessage());
            // Throw rather than return empty — an empty list here would look
            // identical to "genuinely no historical events found", which is a
            // silent failure. Surfacing it as an error (non-200) lets the
            // caller tell the difference between "nothing found" and "broken".
            throw new IllegalStateException(
                    "GDELT lookup failed for " + symbol + " — it's likely rate-limited "
                            + "(free tier allows ~1 request per 5 seconds). Wait a few seconds and retry.", e);
        }

        List<NewsCandidate> candidates = new ArrayList<>();
        for (JsonNode a : articles) {
            LocalDate eventDate = parseGdeltDate(a.path("seendate").asText(null));
            if (eventDate == null) continue;

            candidates.add(new NewsCandidate(
                    a.path("title").asText(""),
                    a.path("url").asText(""),
                    a.path("domain").asText("Unknown"),
                    eventDate
            ));
        }
        return candidates;
    }

    private List<NewsCandidate> mergeDeduped(List<NewsCandidate> recent, List<NewsCandidate> older) {
        // LinkedHashMap keyed by URL to dedupe (GDELT occasionally returns the
        // same article's different mirror/edition URLs across windows) while
        // preserving insertion order before the final sort.
        Map<String, NewsCandidate> byUrl = new LinkedHashMap<>();
        for (NewsCandidate c : recent) byUrl.putIfAbsent(c.url(), c);
        for (NewsCandidate c : older) byUrl.putIfAbsent(c.url(), c);

        List<NewsCandidate> merged = new ArrayList<>(byUrl.values());
        merged.sort(Comparator.comparing(NewsCandidate::eventDate).reversed());
        return merged;
    }

    private void sleepForRateLimit() {
        try {
            Thread.sleep(GDELT_PACING_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private LocalDate parseGdeltDate(String seenDate) {
        if (seenDate == null) return null;
        try {
            return LocalDateTime.parse(seenDate, GDELT_RESPONSE_FORMAT).toLocalDate();
        } catch (Exception e) {
            return null;
        }
    }
}