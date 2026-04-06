package com.cresensolutions.userservice.service;

import com.cresensolutions.userservice.model.UserAccount;
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

        return getCompact(user, now);
    }

    private String getCompact(UserAccount user, Instant now) {
        try {
            return Jwts.builder()
                    .subject(resolveSubject(user))
                    .claims(buildClaims(user))
                    .issuedAt(toDate(now))
                    .expiration(calculateExpiration(now))
                    .signWith(signingKey)
                    .compact();
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String extractUsername(String token) {
        return extractSubject(token);
    }

    @Override
    public boolean isTokenValid(String token, String expectedUsername) {
        try {
            Claims claims = extractAllClaims(token);
            return matchesExpectedUsername(claims, expectedUsername) && isNotExpired(claims);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String extractSubject(String token) {
        return extractAllClaims(token).getSubject();
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            throw new RuntimeException(e);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        }
    }

    private String resolveSubject(UserAccount user) {
        return user == null ? "" : user.getUsername();
    }

    private Map<String, Object> buildClaims(UserAccount user) {
        try {
            if (user == null) {
                return Map.of(
                        "email", "",
                        "role", "",
                        "fullName", "",
                        "active", false
                );
            }

            return Map.of(
                    "email", user.getEmail(),
                    "role", resolveRole(user),
                    "fullName", user.getFullName(),
                    "active", user.isActive()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String resolveRole(UserAccount user) {
        return user.getRole() == null ? "" : user.getRole();
    }

    private Date calculateExpiration(Instant issuedAt) {
        return toDate(issuedAt.plusMillis(expirationMs));
    }

    private Date toDate(Instant instant) {
        return Date.from(instant);
    }

    private boolean matchesExpectedUsername(Claims claims, String expectedUsername) {
        return expectedUsername.equals(claims.getSubject());
    }

    private boolean isNotExpired(Claims claims) {
        return claims.getExpiration().after(currentDate());
    }

    private Date currentDate() {
        return new Date();
    }
}
