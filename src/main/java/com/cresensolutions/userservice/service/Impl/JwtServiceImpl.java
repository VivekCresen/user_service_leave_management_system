package com.cresensolutions.userservice.service.Impl;

import com.cresensolutions.userservice.model.UserAccount;
import com.cresensolutions.userservice.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.InvalidKeyException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class JwtServiceImpl implements JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtServiceImpl(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expiration-ms}") long expirationMs
    ) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMs = expirationMs;
    }

    @Override
    public String generateToken(UserAccount user) {
        Instant now = Instant.now();
        try {
            return Jwts.builder()
                    .subject(user == null ? "" : user.getUsername())
                    .claims(buildClaims(user))
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(now.plusMillis(expirationMs)))
                    .signWith(signingKey)
                    .compact();
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    @Override
    public boolean isTokenValid(String token, String expectedUsername) {
        try {
            Claims claims = extractAllClaims(token);
            return expectedUsername.equals(claims.getSubject())
                    && claims.getExpiration().after(new Date());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> buildClaims(UserAccount user) {
        if (user == null) {
            return Map.of("email", "", "role", "", "fullName", "", "active", false);
        }
        return Map.of(
                "email",    user.getEmail() != null    ? user.getEmail()    : "",
                "role",     user.getRole()  != null    ? user.getRole()     : "",
                "fullName", user.getFullName() != null ? user.getFullName() : "",
                "active",   user.isActive()
        );
    }
}
