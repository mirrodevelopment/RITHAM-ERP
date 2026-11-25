package com.ritham.erp.security;

import com.ritham.erp.common.constants.AppConstants;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * Resolves the effective client IP address while guarding against header spoofing.
 * <p>
 * Proxy headers ({@code CF-Connecting-IP}, {@code X-Forwarded-For}) are only honored
 * when the immediate socket peer is a trusted loopback proxy (e.g., local Cloudflare Tunnel
 * or nginx before Spring's {@code ForwardedHeaderFilter}). Untrusted peers always resolve to
 * {@link HttpServletRequest#getRemoteAddr()}.
 */
public final class ClientIpResolver {

    private static final Set<String> TRUSTED_LOOPBACK_PROXIES = Set.of(
            "127.0.0.1",
            "::1",
            "0:0:0:0:0:0:0:1"
    );

    private ClientIpResolver() {
    }

    public static String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return AppConstants.DEFAULT_CLIENT_IP;
        }
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr != null && TRUSTED_LOOPBACK_PROXIES.contains(remoteAddr.trim())) {
            String cfIp = request.getHeader("CF-Connecting-IP");
            if (cfIp != null && !cfIp.isBlank()) {
                return cfIp.trim();
            }
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                String first = xForwardedFor.split(",")[0].trim();
                if (!first.isEmpty()) {
                    return first;
                }
            }
        }
        if (remoteAddr != null && !remoteAddr.isBlank()) {
            return remoteAddr.trim();
        }
        return AppConstants.DEFAULT_CLIENT_IP;
    }
}
