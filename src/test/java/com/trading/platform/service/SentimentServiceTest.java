package com.trading.platform.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SentimentServiceTest {

    private final SentimentService service = new SentimentService();

    @Test
    void bullishHeadlineIsPositive() {
        SentimentResult r = service.analyze("Infosys shares surge after record profit");
        assertEquals("POSITIVE", r.label());
        assertTrue(r.score() > 0.15);
    }

    @Test
    void bearishHeadlineIsNegative() {
        SentimentResult r = service.analyze("Stock plunges on fraud probe");
        assertEquals("NEGATIVE", r.label());
        assertTrue(r.score() < -0.15);
    }

    @Test
    void headlineWithNoKeywordsIsNeutral() {
        SentimentResult r = service.analyze("Quarterly results announced");
        assertEquals("NEUTRAL", r.label());
        assertEquals(0.0, r.score());
    }

    @Test
    void nullAndBlankInputAreNeutral() {
        assertEquals("NEUTRAL", service.analyze(null).label());
        assertEquals("NEUTRAL", service.analyze("   ").label());
    }

    @Test
    void matchingIsCaseInsensitive() {
        assertEquals("POSITIVE", service.analyze("MARKETS RALLY ON UPGRADE").label());
    }

    @Test
    void scoreStaysWithinMinusOneAndOne() {
        double score = service.analyze("crash plunge plunges fraud scandal scam").score();
        assertTrue(score >= -1.0 && score <= 1.0);
    }

    // ---- Known issues: keyword matching is substring-based, not word-based. ----
    // These tests describe the CORRECT behaviour and are disabled until the fix lands
    // (match on word boundaries). Remove @Disabled to reproduce each bug.

    @Test
    @Disabled("Known issue: 'enterprise' contains 'rise', scored POSITIVE")
    void enterpriseIsNotTreatedAsRise() {
        assertEquals("NEUTRAL", service.analyze("Enterprise software update").label());
    }

    @Test
    @Disabled("Known issue: 'again' contains 'gain', scored POSITIVE")
    void againIsNotTreatedAsGain() {
        assertEquals("NEUTRAL", service.analyze("Will it happen again").label());
    }

    @Test
    @Disabled("Known issue: 'growth' contains 'row', scored NEGATIVE")
    void growthIsNotTreatedAsRow() {
        assertEquals("NEUTRAL", service.analyze("Growth outlook unchanged").label());
    }

    @Test
    @Disabled("Known issue: negation is not understood ('does not fall' scores NEGATIVE)")
    void negationIsRespected() {
        assertEquals("NEUTRAL", service.analyze("Stock does not fall after results").label());
    }
}
