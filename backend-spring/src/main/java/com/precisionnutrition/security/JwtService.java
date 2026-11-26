package com.precisionnutrition.security;

import com.precisionnutrition.model.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class JwtService {

    private static final String DEV_FALLBACK_MARKER = "dev-only-precision-nutrition-jwt-secret";
    private static final int MIN_SECRET_BYTES = 32;

    private final String jwtSecret;
    private final long expirationSeconds;
    private final Environment environment;
    private SecretKey signingKey;

    public JwtService(
            @Value("${app.security.jwt.secret:}") String jwtSecret,
            @Value("${app.security.jwt.expiration-seconds:2592000}") long expirationSeconds,
            Environment environment
    ) {
        this.jwtSecret = jwtSecret;
        this.expirationSeconds = expirationSeconds;
        this.environment = environment;
    }

    @PostConstruct
    void init() {
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");

        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET não configurado. Defina uma chave secreta segura de no mínimo 32 caracteres.");
        }

        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET deve possuir no mínimo 32 bytes (256 bits) para assinatura HS256.");
        }

        if (isProd && jwtSecret.contains(DEV_FALLBACK_MARKER)) {
            throw new IllegalStateException("JWT_SECRET padrão de desenvolvimento não é permitido em perfil 'prod'. Configure JWT_SECRET no ambiente.");
        }

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JwtService inicializado com expiração de {} segundos ({} dias).", expirationSeconds, expirationSeconds / 86400);
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Optional<UUID> validateAndExtractUserId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(subject));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Token JWT inválido ou expirado: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
