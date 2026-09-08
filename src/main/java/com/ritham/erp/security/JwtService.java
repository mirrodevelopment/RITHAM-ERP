package com.ritham.erp.security;

import com.ritham.erp.common.config.AppProperties;
import com.ritham.erp.common.constants.AppConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * JWT utility service for generating and validating access and refresh tokens.
 * Uses JJWT 0.12.x API with HS256 signing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private final AppProperties appProperties;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(appProperties.getJwt().getSecret());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // ── Token generation ──────────────────────────────────────────────────────

    /**
     * Generate a short-lived access token (15 minutes by default).
     */
    public String generateAccessToken(CustomUserDetails userDetails) {
        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put(AppConstants.JWT_CLAIM_TYPE, AppConstants.JWT_TOKEN_TYPE);
        claims.put(AppConstants.JWT_CLAIM_ROLE, userDetails.getRoleName());
        claims.put("employeeId", userDetails.getEmployeeId());
        claims.put("fullName",   userDetails.getFullName());
        if (userDetails.getBranchId() != null) {
            claims.put("branchId", userDetails.getBranchId());
        }
        if (userDetails.getBranchCode() != null) {
            claims.put("branchCode", userDetails.getBranchCode());
        }
        return buildToken(
                userDetails.getUsername(),
                claims,
                appProperties.getJwt().getAccessTokenExpiryMs()
        );
    }

    /**
     * Generate a long-lived refresh token (7 days by default).
     */
    public String generateRefreshToken(String username) {
        return buildToken(
                username,
                Map.of(AppConstants.JWT_CLAIM_TYPE, AppConstants.JWT_REFRESH_TYPE),
                appProperties.getJwt().getRefreshTokenExpiryMs()
        );
    }

    private String buildToken(String subject, Map<String, Object> extraClaims, long expiryMs) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);

        return Jwts.builder()
                .subject(subject)
                .claims(extraClaims)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    // ── Token validation ──────────────────────────────────────────────────────

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    // ── Claims extraction ─────────────────────────────────────────────────────

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractTokenType(String token) {
        return extractAllClaims(token).get(AppConstants.JWT_CLAIM_TYPE, String.class);
    }

    public boolean isAccessToken(String token) {
        return AppConstants.JWT_TOKEN_TYPE.equals(extractTokenType(token));
    }

    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
