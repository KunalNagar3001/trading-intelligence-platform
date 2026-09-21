package com.trading.platform.dto.response;

import java.time.LocalDate;

/**
 * A candidate historical event surfaced from GDELT for a symbol — not
 * persisted. Reviewed by a user and manually promoted into a real
 * CanonicalMoment via POST /api/dna/{symbol}/moments if it's a genuine
 * defining event.
 */
public record NewsCandidate(String headline, String url, String source, LocalDate eventDate) {}