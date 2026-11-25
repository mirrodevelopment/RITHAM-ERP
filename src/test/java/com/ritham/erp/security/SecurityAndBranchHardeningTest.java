package com.ritham.erp.security;

import com.ritham.erp.common.config.AppProperties;
import com.ritham.erp.common.constants.RoleConstants;
import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.module.auth.AuthService;
import com.ritham.erp.module.auth.LoginRateLimiter;
import com.ritham.erp.module.auth.dto.LoginRequest;
import com.ritham.erp.module.auth.dto.RefreshTokenRequest;
import com.ritham.erp.module.auth.entity.RefreshToken;
import com.ritham.erp.module.auth.repository.RefreshTokenRepository;
import com.ritham.erp.module.branch.entity.Branch;
import com.ritham.erp.module.employee.entity.Employee;
import com.ritham.erp.module.employee.entity.Role;
import com.ritham.erp.module.employee.repository.EmployeeRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SecurityAndBranchHardeningTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        BranchContext.clear();
    }

    @Test
    @DisplayName("CustomUserDetailsService throws UsernameNotFoundException so DaoAuthenticationProvider triggers LoginRateLimiter")
    void unknownUsernameTriggersLoginRateLimiter() {
        EmployeeRepository empRepo = mock(EmployeeRepository.class);
        when(empRepo.findByUsername(anyString())).thenReturn(Optional.empty());
        when(empRepo.findByMobileNumber(anyString())).thenReturn(Optional.empty());
        when(empRepo.findByEmployeeCode(anyString())).thenReturn(Optional.empty());

        CustomUserDetailsService uds = new CustomUserDetailsService(empRepo);
        assertThrows(UsernameNotFoundException.class, () -> uds.loadUserByUsername("unknown_user"));
        assertThrows(UsernameNotFoundException.class, () -> uds.loadUserByUsername("   "));

        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(uds);
        provider.setPasswordEncoder(new BCryptPasswordEncoder());
        AuthenticationManager authManager = new ProviderManager(provider);

        AppProperties props = new AppProperties();
        props.getRateLimit().setMaxAttemptsPerUsername(2);
        props.getRateLimit().setMaxAttemptsPerIp(10);
        props.getRateLimit().setLockoutDurationMinutes(15);
        LoginRateLimiter limiter = new LoginRateLimiter(props);

        AuthService authService = new AuthService(
                authManager,
                mock(JwtService.class),
                mock(RefreshTokenRepository.class),
                empRepo,
                props,
                limiter
        );

        LoginRequest req = new LoginRequest();
        req.setUsername("nonexistent_attacker");
        req.setPassword("guess");

        assertEquals(ErrorCode.INVALID_CREDENTIALS,
                assertThrows(AppException.class, () -> authService.login(req, "198.51.100.10")).getErrorCode());
        assertEquals(ErrorCode.INVALID_CREDENTIALS,
                assertThrows(AppException.class, () -> authService.login(req, "198.51.100.10")).getErrorCode());
        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED,
                assertThrows(AppException.class, () -> authService.login(req, "198.51.100.10")).getErrorCode());
    }

    @Test
    @DisplayName("isGlobalAdmin is true only when ADMIN has null branch")
    void customUserDetailsGlobalAdminRequiresNullBranch() {
        Role adminRole = Role.builder().id(1L).name(RoleConstants.ADMIN).build();
        Branch branch = Branch.builder().id(2L).name("Coimbatore").branchCode("CBE").build();

        Employee globalAdmin = Employee.builder().id(1L).username("gadmin").role(adminRole).branch(null).isActive(true).build();
        Employee branchAdmin = Employee.builder().id(2L).username("badmin").role(adminRole).branch(branch).isActive(true).build();

        assertTrue(new CustomUserDetails(globalAdmin).isGlobalAdmin());
        assertFalse(new CustomUserDetails(branchAdmin).isGlobalAdmin());
    }

    @Test
    @DisplayName("JwtAuthFilter ignores ?token= query parameter and rejects disabled accounts")
    void jwtFilterRejectsQueryTokenAndDisabledUsers() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        CustomUserDetailsService uds = mock(CustomUserDetailsService.class);
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, uds);

        // 1. Query-parameter token must be ignored
        HttpServletRequest reqQuery = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(reqQuery.getHeader("Authorization")).thenReturn(null);

        filter.doFilter(reqQuery, resp, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(jwtService, never()).extractUsername(anyString());

        // 2. Bearer token for disabled employee must not populate SecurityContext
        HttpServletRequest reqDisabled = mock(HttpServletRequest.class);
        Role staffRole = Role.builder().id(3L).name(RoleConstants.PRODUCTION_EMPLOYEE).build();
        Employee disabledEmp = Employee.builder().id(5L).username("disabled_user").role(staffRole).isActive(false).build();
        CustomUserDetails disabledDetails = new CustomUserDetails(disabledEmp);

        when(reqDisabled.getHeader("Authorization")).thenReturn("Bearer valid.jwt.token");
        when(jwtService.extractUsername("valid.jwt.token")).thenReturn("disabled_user");
        when(uds.loadUserByUsername("disabled_user")).thenReturn(disabledDetails);
        when(jwtService.isTokenValid("valid.jwt.token", disabledDetails)).thenReturn(true);

        filter.doFilter(reqDisabled, resp, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("AuthService.refresh blocks deactivated employees")
    void refreshRejectsDisabledEmployee() {
        RefreshTokenRepository refreshRepo = mock(RefreshTokenRepository.class);
        Role role = Role.builder().id(3L).name(RoleConstants.PRODUCTION_EMPLOYEE).build();
        Employee disabledEmp = Employee.builder().id(9L).username("ex_staff").role(role).isActive(false).build();
        RefreshToken token = RefreshToken.builder()
                .id(1L).token("refresh-123").employee(disabledEmp)
                .expiresAt(LocalDateTime.now().plusDays(1)).isRevoked(false).build();

        when(refreshRepo.findByToken("refresh-123")).thenReturn(Optional.of(token));

        AuthService authService = new AuthService(
                mock(AuthenticationManager.class),
                mock(JwtService.class),
                refreshRepo,
                mock(EmployeeRepository.class),
                new AppProperties(),
                new LoginRateLimiter(new AppProperties())
        );

        RefreshTokenRequest req = new RefreshTokenRequest();
        req.setRefreshToken("refresh-123");

        AppException ex = assertThrows(AppException.class, () -> authService.refresh(req));
        assertEquals(ErrorCode.ACCOUNT_DISABLED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Successful login for one user does not wipe IP failure count against another account")
    void successfulLoginDoesNotResetIpFailures() {
        AppProperties props = new AppProperties();
        props.getRateLimit().setMaxAttemptsPerUsername(20);
        props.getRateLimit().setMaxAttemptsPerIp(3);
        props.getRateLimit().setLockoutDurationMinutes(15);

        LoginRateLimiter limiter = new LoginRateLimiter(props);
        String ip = "203.0.113.55";
        limiter.recordFailedAttempt(ip, "admin");
        limiter.recordFailedAttempt(ip, "admin");
        limiter.recordSuccessfulLogin(ip, "valid_user");
        limiter.recordFailedAttempt(ip, "admin");

        assertTrue(limiter.isIpLocked(ip), "3rd failure from same IP must still trigger IP lockout");
    }

    @Test
    @DisplayName("ClientIpResolver trusts X-Forwarded-For only from loopback proxy")
    void clientIpResolverGuardsAgainstHeaderSpoofing() {
        HttpServletRequest untrustedReq = mock(HttpServletRequest.class);
        when(untrustedReq.getRemoteAddr()).thenReturn("198.51.100.42");
        assertEquals("198.51.100.42", ClientIpResolver.extractClientIp(untrustedReq));

        HttpServletRequest proxyReq = mock(HttpServletRequest.class);
        when(proxyReq.getRemoteAddr()).thenReturn("127.0.0.1");
        when(proxyReq.getHeader("CF-Connecting-IP")).thenReturn(null);
        when(proxyReq.getHeader("X-Forwarded-For")).thenReturn("203.0.113.7, 10.0.0.1");
        assertEquals("203.0.113.7", ClientIpResolver.extractClientIp(proxyReq));
    }
}
