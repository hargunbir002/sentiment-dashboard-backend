package com.sentimentdashboard.sentiment_dashboard.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expiryMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiry-ms:86400000}") long expiryMs) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expiryMs = expiryMs;
    }

    public String generate(String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiryMs))
                .signWith(key)
                .compact();
    }

    /** Returns username if token is valid, otherwise null. */
    public String validate(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return c.getSubject();
        } catch (Exception e) {
            return null;
        }
    }
}