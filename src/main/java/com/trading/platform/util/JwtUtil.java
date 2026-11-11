package com.trading.platform.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Date;

/**
 * Creates and validates login tokens.
 * <p>
 * The signing key comes from configuration ({@code app.jwt.secret}, set through the {@code JWT_SECRET}
 * environment variable) and must be at least 32 characters. If it is not set, a random key is generated at
 * startup: the app still works, but every token stops being valid on restart, and tokens are not valid across
 * several instances.
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    static final int MIN_SECRET_BYTES = 32; // HS256 needs a 256-bit key

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtUtil(@Value("${app.jwt.secret:}") String secret,
                   @Value("${app.jwt.expiration-ms:86400000}") long expirationMs) {
        this.secretKey = buildKey(secret);
        this.expirationMs = expirationMs;
    }

    private static SecretKey buildKey(String secret) {
        if (secret == null || secret.isBlank()) {
            byte[] random = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(random);
            log.warn("JWT_SECRET is not set: using a random signing key. All logins become invalid when the "
                    + "app restarts. Set JWT_SECRET (at least {} characters) for a stable key.", MIN_SECRET_BYTES);
            return Keys.hmacShaKeyFor(random);
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET is too short: it must be at least " + MIN_SECRET_BYTES + " characters");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(secretKey)
                .compact();
    }

    public String extractEmail(String token) {
        return getClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = getClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
