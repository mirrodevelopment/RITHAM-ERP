package com.ritham.erp.module.auth;

import com.ritham.erp.common.config.AppProperties;
import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class LoginRateLimiterTest {

    private AppProperties appProperties;
    private LoginRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getRateLimit().setMaxAttemptsPerUsername(5);
        appProperties.getRateLimit().setMaxAttemptsPerIp(10);
        appProperties.getRateLimit().setLockoutDurationMinutes(15);

        rateLimiter = new LoginRateLimiter(appProperties);
        rateLimiter.reset();
    }

    @Test
    @DisplayName("Fresh IP and username should not be blocked")
    void freshClientNotBlocked() {
        assertDoesNotThrow(() -> rateLimiter.checkRateLimit("192.168.1.100", "admin"));
        assertFalse(rateLimiter.isUsernameLocked("admin"));
        assertFalse(rateLimiter.isIpLocked("192.168.1.100"));
    }

    @Test
    @DisplayName("Username is locked out after 5 consecutive failed attempts")
    void usernameLockedOutAfterThreshold() {
        String username = "manager1";
        String ip = "192.168.1.50";

        for (int i = 1; i <= 4; i++) {
            rateLimiter.recordFailedAttempt(ip, username);
            assertDoesNotThrow(() -> rateLimiter.checkRateLimit(ip, username),
                    "Attempt " + i + " should not trigger lockout yet");
        }

        // 5th failed attempt triggers lockout
        rateLimiter.recordFailedAttempt(ip, username);

        assertTrue(rateLimiter.isUsernameLocked(username));

        AppException ex = assertThrows(AppException.class, () ->
                rateLimiter.checkRateLimit(ip, username));
        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, ex.getErrorCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getHttpStatus());
    }

    @Test
    @DisplayName("IP is locked out after 10 failed attempts across different usernames")
    void ipLockedOutAfterThresholdAcrossDifferentUsers() {
        String ip = "203.0.113.195";

        for (int i = 1; i <= 9; i++) {
            final String targetedUser = "user" + i;
            rateLimiter.recordFailedAttempt(ip, targetedUser);
            assertDoesNotThrow(() -> rateLimiter.checkRateLimit(ip, targetedUser));
        }

        // 10th failed attempt from this IP
        rateLimiter.recordFailedAttempt(ip, "user10");

        assertTrue(rateLimiter.isIpLocked(ip));

        // Even an untargeted user is blocked from this malicious IP
        AppException ex = assertThrows(AppException.class, () ->
                rateLimiter.checkRateLimit(ip, "fresh_user"));
        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, ex.getErrorCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getHttpStatus());
    }

    @Test
    @DisplayName("Successful login clears failed attempts for the username and resets IP counter")
    void successfulLoginResetsFailureCounts() {
        String username = "receptionist";
        String ip = "10.0.0.5";

        // 3 failed attempts
        for (int i = 0; i < 3; i++) {
            rateLimiter.recordFailedAttempt(ip, username);
        }

        // User remembers password and successfully logs in
        rateLimiter.recordSuccessfulLogin(ip, username);

        // 3 more failed attempts should not trigger lockout because previous count was cleared
        for (int i = 0; i < 3; i++) {
            rateLimiter.recordFailedAttempt(ip, username);
        }

        assertDoesNotThrow(() -> rateLimiter.checkRateLimit(ip, username));
        assertFalse(rateLimiter.isUsernameLocked(username));
    }

    @Test
    @DisplayName("Case-insensitivity and whitespace trimming for username and IP normalization")
    void normalizationWorksCorrectly() {
        String ip = " 192.168.1.1 ";
        String username = " Admin ";

        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailedAttempt(ip, username);
        }

        assertTrue(rateLimiter.isUsernameLocked("admin"));
        assertTrue(rateLimiter.isUsernameLocked("ADMIN"));
        assertTrue(rateLimiter.isUsernameLocked(" Admin "));

        assertThrows(AppException.class, () ->
                rateLimiter.checkRateLimit("192.168.1.1", "admin"));
    }
}
