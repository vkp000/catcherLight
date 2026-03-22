package com.incoin.demo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expiryMs;

    public JwtUtil(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.expiry-hours}") long expiryHours
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMs  = expiryHours * 3600L * 1000L;
    }

    /** Generate a signed JWT whose subject is the internal userId. */
    public String generate(String userId) {
        return Jwts.builder()
            .subject(userId)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expiryMs))
            .signWith(secretKey)
            .compact();
    }

    /** Extract the userId (subject) from a valid token. Throws on invalid/expired. */
    public String extractUserId(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        return claims.getSubject();
    }

    /** Returns true only if the token is structurally valid and not expired. */
    public boolean isValid(String token) {
        try {
            extractUserId(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
