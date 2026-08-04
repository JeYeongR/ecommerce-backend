package com.ecommerce.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    private static final String TYPE_CLAIM = "type";

    private final SecretKey key;
    private final long expirationMs;

    public JwtProvider(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.expiration-ms}") long expirationMs
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(Long id, AccountType type) {
        Date now = new Date();

        return Jwts.builder()
            .subject(String.valueOf(id))
            .claim(TYPE_CLAIM, type.name())
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expirationMs))
            .signWith(key)
            .compact();
    }

    public Optional<AuthPrincipal> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long id = Long.valueOf(claims.getSubject());
            AccountType type = AccountType.valueOf(claims.get(TYPE_CLAIM, String.class));

            return Optional.of(new AuthPrincipal(id, type));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
