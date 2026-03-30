package com.example.demo.security;

import com.example.demo.model.Role;
import com.example.demo.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

// Utility class for creating, parsing, and validating JWT tokens.
// Uses HMAC-SHA signing — the same secret key signs and verifies.
@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final Duration expiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration:24h}") Duration expiration) {
        // Key must be at least 256 bits (32 bytes) for HMAC-SHA256
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    // Builds a signed JWT with username as subject and role as a custom claim
    public String generateToken(User user, Role role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("role", role.name())
                .claim("userId", user.getId().toString())
                .claim("email", user.getEmail())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration.toMillis()))
                .signWith(signingKey)
                .compact();
    }

    // Extracts the username (subject) from a token
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractEmail(String token) {
        return parseClaims(token).get("email", String.class);
    }

    // Extracts the role claim from a token
    public Role extractRole(String token) {
        return Role.valueOf(parseClaims(token).get("role", String.class));
    }

    public String extractUserId(String token) {
        return parseClaims(token).get("userId", String.class);
    }

    // Returns true if the token is valid and not expired
    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Returns all claims from the token in a single parse.
    public Claims extractAllClaims(String token) {
        return parseClaims(token);
    }

    // Parses and verifies the token signature + expiration in one step.
    // Throws if the token is tampered with, expired, or malformed.
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

}