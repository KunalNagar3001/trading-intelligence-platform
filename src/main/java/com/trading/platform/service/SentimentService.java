package com.trading.platform.service;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

/**
 * Rule-based sentiment scoring — a weighted keyword lexicon, not a trained
 * model. Good enough to demonstrate the concept and surface obviously
 * bullish/bearish headlines; it will misjudge sarcasm, negation ("shares
 * did NOT fall"), and anything requiring real context. Swap this out for
 * a proper NLP/transformer-based classifier later if the project needs
 * better accuracy — the interface (analyze(text) -> SentimentResult) stays
 * the same either way, so nothing else has to change to make that swap.
 */
@Service
public class SentimentService {

    private static final Map<String, Double> POSITIVE_WEIGHTS = Map.ofEntries(
            Map.entry("surge", 1.0), Map.entry("soar", 1.0), Map.entry("rally", 0.8),
            Map.entry("jump", 0.7), Map.entry("gain", 0.6), Map.entry("gains", 0.6),
            Map.entry("rise", 0.5), Map.entry("rises", 0.5), Map.entry("upgrade", 0.8),
            Map.entry("outperform", 0.7), Map.entry("buy", 0.6), Map.entry("beat", 0.6),
            Map.entry("beats", 0.6), Map.entry("profit", 0.5), Map.entry("record high", 0.9),
            Map.entry("bullish", 0.8), Map.entry("top gainers", 0.7), Map.entry("recommend", 0.5)
    );

    private static final Map<String, Double> NEGATIVE_WEIGHTS = Map.ofEntries(
            Map.entry("crash", -1.0), Map.entry("plunge", -1.0), Map.entry("plunges", -1.0),
            Map.entry("tumble", -0.8), Map.entry("stumble", -0.8), Map.entry("stumbles", -0.8),
            Map.entry("fall", -0.6), Map.entry("falls", -0.6), Map.entry("fell", -0.6),
            Map.entry("decline", -0.6), Map.entry("drop", -0.6), Map.entry("drops", -0.6),
            Map.entry("erase", -0.7), Map.entry("erasing", -0.7), Map.entry("downgrade", -0.8),
            Map.entry("probe", -0.7), Map.entry("fraud", -0.9), Map.entry("scandal", -0.9),
            Map.entry("sell", -0.5), Map.entry("loss", -0.6), Map.entry("losses", -0.6),
            Map.entry("bearish", -0.8), Map.entry("recall", -0.6), Map.entry("scam", -0.9),
            Map.entry("default", -0.8), Map.entry("investigation", -0.6), Map.entry("row", -0.4)
    );

    private static final double POSITIVE_THRESHOLD = 0.15;
    private static final double NEGATIVE_THRESHOLD = -0.15;

    public SentimentResult analyze(String text) {
        if (text == null || text.isBlank()) {
            return new SentimentResult("NEUTRAL", 0.0);
        }

        String lower = text.toLowerCase(Locale.ROOT);
        double total = 0.0;
        int matches = 0;

        for (Map.Entry<String, Double> entry : POSITIVE_WEIGHTS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                total += entry.getValue();
                matches++;
            }
        }
        for (Map.Entry<String, Double> entry : NEGATIVE_WEIGHTS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                total += entry.getValue();
                matches++;
            }
        }

        if (matches == 0) {
            return new SentimentResult("NEUTRAL", 0.0);
        }

        // Average rather than sum, so one very long article doesn't just
        // accumulate an extreme score purely from having more words to match.
        double score = clamp(total / matches, -1.0, 1.0);

        String label;
        if (score >= POSITIVE_THRESHOLD) {
            label = "POSITIVE";
        } else if (score <= NEGATIVE_THRESHOLD) {
            label = "NEGATIVE";
        } else {
            label = "NEUTRAL";
        }

        return new SentimentResult(label, score);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}