package com.ritham.erp.security;

import com.ritham.erp.common.constants.AppConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT authentication filter — runs once per request.
 *
 * <ol>
 *   <li>Reads the {@code Authorization: Bearer <token>} header</li>
 *   <li>Validates the JWT and verifies it is an access token (not refresh)</li>
 *   <li>Loads {@link CustomUserDetails} and populates {@link SecurityContextHolder}</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService                jwtService;
    private final CustomUserDetailsService  userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest  request,
            HttpServletResponse response,
            FilterChain         filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader(AppConstants.JWT_HEADER);
        String jwt = null;

        if (authHeader != null && authHeader.startsWith(AppConstants.JWT_PREFIX)) {
            jwt = authHeader.substring(AppConstants.JWT_PREFIX.length());
        } else if (request.getParameter("token") != null && !request.getParameter("token").isBlank()) {
            jwt = request.getParameter("token").trim();
        }

        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }
        final String username;

        try {
            username = jwtService.extractUsername(jwt);
        } catch (Exception ex) {
            log.debug("Could not extract username from JWT: {}", ex.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Only set authentication if not already present and it's an access token
            if (username != null
                    && SecurityContextHolder.getContext().getAuthentication() == null
                    && jwtService.isAccessToken(jwt)) {

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("Authenticated user '{}' via JWT", username);

                    // Set up BranchContext for multi-tenancy with audit traceability
                    if (userDetails instanceof CustomUserDetails cud) {
                        String branchHeader = request.getHeader(AppConstants.BRANCH_HEADER);
                        if (cud.isGlobalAdmin()) {
                            if (branchHeader != null && !branchHeader.isBlank() && !"ALL".equalsIgnoreCase(branchHeader.trim())) {
                                try {
                                    Long targetBranchId = Long.parseLong(branchHeader.trim());
                                    BranchContext.setBranchId(targetBranchId);
                                    log.info("AUDIT: Admin '{}' switched branch context to branchId={} for request [{} {}]",
                                            username, targetBranchId, request.getMethod(), request.getRequestURI());
                                } catch (NumberFormatException e) {
                                    BranchContext.setBranchId(null);
                                    log.warn("AUDIT: Admin '{}' provided malformed X-Branch-Id '{}' for request [{} {}] - defaulting to ALL branches",
                                            username, branchHeader, request.getMethod(), request.getRequestURI());
                                }
                            } else {
                                BranchContext.setBranchId(null);
                                if ("ALL".equalsIgnoreCase(branchHeader != null ? branchHeader.trim() : "")) {
                                    log.debug("AUDIT: Admin '{}' explicitly requested ALL branches for request [{} {}]",
                                            username, request.getMethod(), request.getRequestURI());
                                }
                            }
                        } else {
                            BranchContext.setBranchId(cud.getBranchId());
                            if (branchHeader != null && !branchHeader.isBlank()) {
                                log.warn("AUDIT SECURITY ALERT: Non-admin user '{}' (assigned branchId={}) attempted unauthorized branch context switch using X-Branch-Id='{}' on [{} {}]",
                                        username, cud.getBranchId(), branchHeader, request.getMethod(), request.getRequestURI());
                            }
                        }
                    }
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            BranchContext.clear();
        }
    }
}
