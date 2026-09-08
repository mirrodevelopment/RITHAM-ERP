package com.ritham.erp.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Strongly-typed binding for the {@code app.*} configuration block
 * in {@code application.yaml}.
 */
@Component
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private RateLimit rateLimit = new RateLimit();

    @Getter
    @Setter
    public static class RateLimit {
        /** Maximum failed attempts allowed per username before lockout */
        private int maxAttemptsPerUsername = 5;
        /** Maximum failed attempts allowed per IP address before lockout */
        private int maxAttemptsPerIp = 10;
        /** Lockout duration in minutes */
        private long lockoutDurationMinutes = 15;
    }

    @Getter
    @Setter
    public static class Jwt {
        /** Base64-encoded 256-bit signing secret */
        private String secret;
        /** Access token expiry in milliseconds (default: 15 min) */
        private long accessTokenExpiryMs = 900_000L;
        /** Refresh token expiry in milliseconds (default: 7 days) */
        private long refreshTokenExpiryMs = 604_800_000L;
    }

    @Getter
    @Setter
    public static class Cors {
        /** List of allowed origins for CORS pre-flight and actual requests */
        private List<String> allowedOrigins = List.of("http://localhost:3000");
    }
}
