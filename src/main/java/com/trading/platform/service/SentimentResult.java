package com.trading.platform.service;

/**
 * label is one of POSITIVE / NEGATIVE / NEUTRAL.
 * score ranges from -1.0 (strongly negative) to 1.0 (strongly positive).
 */
public record SentimentResult(String label, double score) {
}