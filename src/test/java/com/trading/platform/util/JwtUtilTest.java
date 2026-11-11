package com.trading.platform.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private static final String SECRET = "a-test-secret-that-is-at-least-32-characters-long";
    private static final long ONE_DAY = 86_400_000L;

    @Test
    void tokenRoundTripsTheEmail() {
        JwtUtil jwt = new JwtUtil(SECRET, ONE_DAY);

        String token = jwt.generateToken("asha@example.com");

        assertTrue(jwt.isTokenValid(token));
        assertEquals("asha@example.com", jwt.extractEmail(token));
    }

    @Test
    void tokenSignedWithADifferentSecretIsRejected() {
        JwtUtil issuer = new JwtUtil(SECRET, ONE_DAY);
        JwtUtil other = new JwtUtil("a-completely-different-secret-of-32-plus-chars", ONE_DAY);

        assertFalse(other.isTokenValid(issuer.generateToken("asha@example.com")));
    }

    @Test
    void expiredTokenIsRejected() {
        JwtUtil jwt = new JwtUtil(SECRET, -1_000L); // expires one second before it is issued

        assertFalse(jwt.isTokenValid(jwt.generateToken("asha@example.com")));
    }

    @Test
    void tamperedAndGarbageTokensAreRejected() {
        JwtUtil jwt = new JwtUtil(SECRET, ONE_DAY);
        String token = jwt.generateToken("asha@example.com");
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertFalse(jwt.isTokenValid(tampered));
        assertFalse(jwt.isTokenValid("not-a-jwt"));
        assertFalse(jwt.isTokenValid(""));
    }

    @Test
    void tooShortSecretIsRefusedSoTheAppFailsToStart() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new JwtUtil("too-short", ONE_DAY));
        assertTrue(ex.getMessage().contains("at least 32"));
    }

    @Test
    void secretOfExactlyThirtyTwoCharactersIsAccepted() {
        JwtUtil jwt = new JwtUtil("12345678901234567890123456789012", ONE_DAY);

        assertTrue(jwt.isTokenValid(jwt.generateToken("asha@example.com")));
    }

    @Test
    void missingSecretFallsBackToARandomKeyThatIsNotSharedBetweenInstances() {
        JwtUtil first = new JwtUtil("", ONE_DAY);
        JwtUtil second = new JwtUtil(null, ONE_DAY);

        String token = first.generateToken("asha@example.com");
        assertTrue(first.isTokenValid(token));
        assertFalse(second.isTokenValid(token));
    }
}
