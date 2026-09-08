package com.ritham.erp.module.auth;

import com.ritham.erp.common.config.AppProperties;
import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory rate limiter for authentication endpoints.
 * Protects against brute-force password guessing and credential-stuffing attacks
 * by tracking failed login attempts per username and per client IP address.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginRateLimiter {

    private final AppProperties appProperties;

    private final ConcurrentHashMap<String, AttemptTracker> usernameAttempts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AttemptTracker> ipAttempts = new ConcurrentHashMap<>();

    /**
     * Checks whether the given client IP or username is currently blocked due to excessive failed attempts.
     *
     * @param clientIp the remote IP of the client
     * @param username the login username or identifier attempted
     * @throws AppException with {@link ErrorCode#RATE_LIMIT_EXCEEDED} if blocked
     */
    public void checkRateLimit(String clientIp, String username) {
        Instant now = Instant.now();
        String normalizedIp = normalize(clientIp);
        String normalizedUsername = normalize(username);

        if (normalizedIp != null) {
            AttemptTracker ipTracker = ipAttempts.get(normalizedIp);
            if (ipTracker != null && ipTracker.isLocked(now)) {
                log.warn("Login attempt rejected: IP '{}' is temporarily locked out due to excessive failed attempts", normalizedIp);
                throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
            }
        }

        if (normalizedUsername != null) {
            AttemptTracker userTracker = usernameAttempts.get(normalizedUsername);
            if (userTracker != null && userTracker.isLocked(now)) {
                log.warn("Login attempt rejected: Username '{}' is temporarily locked out due to excessive failed attempts", normalizedUsername);
                throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
            }
        }
    }

    /**
     * Records a failed login attempt for the given client IP and username.
     * If the threshold is reached, locks out further attempts for the configured lockout duration.
     *
     * @param clientIp the remote IP of the client
     * @param username the username attempted
     */
    public void recordFailedAttempt(String clientIp, String username) {
        Instant now = Instant.now();
        String normalizedIp = normalize(clientIp);
        String normalizedUsername = normalize(username);

        int maxAttemptsPerIp = appProperties.getRateLimit().getMaxAttemptsPerIp();
        int maxAttemptsPerUser = appProperties.getRateLimit().getMaxAttemptsPerUsername();
        long lockoutMinutes = appProperties.getRateLimit().getLockoutDurationMinutes();

        if (normalizedIp != null) {
            ipAttempts.compute(normalizedIp, (k, v) -> {
                AttemptTracker tracker = (v != null) ? v : new AttemptTracker();
                tracker.recordFailure(maxAttemptsPerIp, lockoutMinutes, now);
                return tracker;
            });
        }

        if (normalizedUsername != null) {
            usernameAttempts.compute(normalizedUsername, (k, v) -> {
                AttemptTracker tracker = (v != null) ? v : new AttemptTracker();
                tracker.recordFailure(maxAttemptsPerUser, lockoutMinutes, now);
                return tracker;
            });
        }

        pruneExpiredEntriesIfNeeded(now, lockoutMinutes);
    }

    /**
     * Resets the failure counter on a successful authentication.
     *
     * @param clientIp the remote IP of the client
     * @param username the username successfully authenticated
     */
    public void recordSuccessfulLogin(String clientIp, String username) {
        String normalizedIp = normalize(clientIp);
        String normalizedUsername = normalize(username);

        if (normalizedUsername != null) {
            usernameAttempts.remove(normalizedUsername);
        }
        if (normalizedIp != null) {
            AttemptTracker tracker = ipAttempts.get(normalizedIp);
            if (tracker != null) {
                tracker.reset();
            }
        }
    }

    /**
     * Clears all tracked rate-limiting state. Useful for test environments.
     */
    public void reset() {
        usernameAttempts.clear();
        ipAttempts.clear();
    }

    /**
     * Returns true if the given username is currently locked out.
     */
    public boolean isUsernameLocked(String username) {
        String normalized = normalize(username);
        if (normalized == null) return false;
        AttemptTracker tracker = usernameAttempts.get(normalized);
        return tracker != null && tracker.isLocked(Instant.now());
    }

    /**
     * Returns true if the given IP address is currently locked out.
     */
    public boolean isIpLocked(String clientIp) {
        String normalized = normalize(clientIp);
        if (normalized == null) return false;
        AttemptTracker tracker = ipAttempts.get(normalized);
        return tracker != null && tracker.isLocked(Instant.now());
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private void pruneExpiredEntriesIfNeeded(Instant now, long lockoutMinutes) {
        // Run light eviction if the tracked maps grow beyond 1,000 entries
        if (usernameAttempts.size() > 1000) {
            usernameAttempts.entrySet().removeIf(e -> e.getValue().isExpired(now, lockoutMinutes));
        }
        if (ipAttempts.size() > 1000) {
            ipAttempts.entrySet().removeIf(e -> e.getValue().isExpired(now, lockoutMinutes));
        }
    }

    /**
     * Holds attempt counts and lockout state for a single entity (IP or Username).
     */
    static class AttemptTracker {
        private int failedAttempts;
        private Instant lockoutUntil;
        private Instant lastAttemptAt;

        public AttemptTracker() {
            this.failedAttempts = 0;
            this.lockoutUntil = null;
            this.lastAttemptAt = Instant.now();
        }

        public synchronized boolean isLocked(Instant now) {
            if (lockoutUntil == null) {
                return false;
            }
            if (now.isBefore(lockoutUntil)) {
                return true;
            }
            // Lockout expired
            lockoutUntil = null;
            failedAttempts = 0;
            return false;
        }

        public synchronized void recordFailure(int maxAttempts, long lockoutMinutes, Instant now) {
            // If the time elapsed since the last failure exceeds the lockout window, reset count
            if (lastAttemptAt != null && now.isAfter(lastAttemptAt.plusSeconds(lockoutMinutes * 60))) {
                failedAttempts = 0;
            }
            lastAttemptAt = now;
            failedAttempts++;
            if (failedAttempts >= maxAttempts) {
                lockoutUntil = now.plusSeconds(lockoutMinutes * 60);
            }
        }

        public synchronized void reset() {
            failedAttempts = 0;
            lockoutUntil = null;
            lastAttemptAt = null;
        }

        public synchronized boolean isExpired(Instant now, long lockoutMinutes) {
            if (lockoutUntil != null && now.isBefore(lockoutUntil)) {
                return false;
            }
            if (lastAttemptAt != null && now.isBefore(lastAttemptAt.plusSeconds(lockoutMinutes * 60))) {
                return false;
            }
            return true;
        }

        public synchronized int getFailedAttempts() {
            return failedAttempts;
        }
    }
}
