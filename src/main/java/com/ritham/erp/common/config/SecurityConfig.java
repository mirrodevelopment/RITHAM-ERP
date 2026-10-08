package com.ritham.erp.common.config;

import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import tools.jackson.databind.ObjectMapper;
import com.ritham.erp.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

/**
 * Spring Security configuration — JWT stateless, role-based access control.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter             jwtAuthFilter;
    private final com.ritham.erp.security.CustomUserDetailsService userDetailsService;
    private final CorsConfigurationSource   corsConfigurationSource;

    // ── Static assets to ignore from security filter chain ────────────────────
    private static final String[] STATIC_ASSETS = {
            "/",
            "/index.html",
            "/error",
            "/desks/**",
            "/css/**",
            "/js/**",
            "/theme/**",
            "/components/**",
            "/icons/**",
            "/images/**",
            "/favicon.ico"
    };

    // ── Public API endpoints ──────────────────────────────────────────────────
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/login",
            "/api/auth/refresh",
            "/actuator/health",
            "/actuator/info"
    };

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring().requestMatchers(STATIC_ASSETS);
    }

    // ── Security filter chain ─────────────────────────────────────────────────

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper)
            throws Exception {

        http
            // Disable CSRF (stateless JWT API)
            .csrf(csrf -> csrf.disable())

            // CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource))

            // Session — fully stateless
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // URL-based authorization
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                    // SEC-1 FIX: GET is allowed, but mutations require ADMIN or OPERATIONS_MANAGER
                    .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/production-stages/**").permitAll()
                    .requestMatchers("/api/production-stages/**").hasAnyRole("ADMIN", "OPERATIONS_MANAGER")
                    .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                    .requestMatchers("/operations/**").hasAnyRole("ADMIN", "OPERATIONS_MANAGER")
                    .requestMatchers("/production/**")
                            .hasAnyRole("ADMIN", "OPERATIONS_MANAGER", "PRODUCTION_EMPLOYEE")
                    .anyRequest().authenticated()
            )

            // Custom 401 response (JSON, not redirect)
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((request, response, authException) -> {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write(
                                objectMapper.writeValueAsString(
                                        ApiResponse.error(ErrorCode.TOKEN_MISSING.getMessage())
                                )
                        );
                    })
                    .accessDeniedHandler((request, response, accessDeniedException) -> {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.getWriter().write(
                                objectMapper.writeValueAsString(
                                        ApiResponse.error(ErrorCode.ACCESS_DENIED.getMessage())
                                )
                        );
                    })
            )

            // Authentication provider
            .authenticationProvider(authenticationProvider())

            // JWT filter before username/password filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ── Beans ─────────────────────────────────────────────────────────────────

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
