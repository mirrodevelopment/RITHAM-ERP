package com.ritham.erp.module.auth;

import com.ritham.erp.common.config.AppProperties;
import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.module.auth.dto.LoginRequest;
import com.ritham.erp.module.auth.repository.RefreshTokenRepository;
import com.ritham.erp.module.employee.entity.Employee;
import com.ritham.erp.module.employee.entity.Role;
import com.ritham.erp.module.employee.repository.EmployeeRepository;
import com.ritham.erp.security.CustomUserDetails;
import com.ritham.erp.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceRateLimitTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private EmployeeRepository employeeRepository;

    private AppProperties appProperties;
    private LoginRateLimiter loginRateLimiter;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getRateLimit().setMaxAttemptsPerUsername(3); // Lower threshold for quick testing
        appProperties.getRateLimit().setMaxAttemptsPerIp(5);
        appProperties.getRateLimit().setLockoutDurationMinutes(15);

        loginRateLimiter = new LoginRateLimiter(appProperties);
        loginRateLimiter.reset();

        authService = new AuthService(
                authenticationManager,
                jwtService,
                refreshTokenRepository,
                employeeRepository,
                appProperties,
                loginRateLimiter
        );
    }

    @Test
    @DisplayName("Consecutive failed login attempts trigger RATE_LIMIT_EXCEEDED and bypass authenticationManager")
    void failedLoginsTriggerLockoutAndBypassAuthManager() {
        LoginRequest request = new LoginRequest();
        request.setUsername("target_user");
        request.setPassword("wrong_password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        String clientIp = "192.168.1.20";

        // Attempts 1, 2, 3 should fail with INVALID_CREDENTIALS
        for (int i = 1; i <= 3; i++) {
            AppException ex = assertThrows(AppException.class, () ->
                    authService.login(request, clientIp));
            assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
        }

        // authenticationManager was invoked 3 times
        verify(authenticationManager, times(3)).authenticate(any(UsernamePasswordAuthenticationToken.class));

        // Attempt 4 should immediately throw RATE_LIMIT_EXCEEDED (HTTP 429)
        AppException rateLimitEx = assertThrows(AppException.class, () ->
                authService.login(request, clientIp));
        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, rateLimitEx.getErrorCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, rateLimitEx.getHttpStatus());

        // Crucial security check: authenticationManager was NOT called again on attempt 4
        verify(authenticationManager, times(3)).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("Consecutive failed verifyAdminPassword attempts trigger RATE_LIMIT_EXCEEDED and bypass authenticationManager")
    void failedVerifyAdminPasswordTriggersLockoutAndBypassAuthManager() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        String clientIp = "10.0.0.55";
        String adminUser = "admin_master";

        // Attempts 1, 2, 3 should fail with INVALID_CREDENTIALS
        for (int i = 1; i <= 3; i++) {
            AppException ex = assertThrows(AppException.class, () ->
                    authService.verifyAdminPassword(adminUser, "wrong_pass", clientIp));
            assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
        }

        // authenticationManager was called 3 times
        verify(authenticationManager, times(3)).authenticate(any(UsernamePasswordAuthenticationToken.class));

        // Attempt 4 should immediately throw RATE_LIMIT_EXCEEDED without touching authenticationManager
        AppException rateLimitEx = assertThrows(AppException.class, () ->
                authService.verifyAdminPassword(adminUser, "wrong_pass", clientIp));
        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, rateLimitEx.getErrorCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, rateLimitEx.getHttpStatus());

        verify(authenticationManager, times(3)).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("Successful verifyAdminPassword resets failure tracker for the admin user")
    void successfulVerifyAdminPasswordResetsRateLimiter() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        String clientIp = "10.0.0.60";
        String adminUser = "admin_reset_test";

        // Fail 2 times
        for (int i = 1; i <= 2; i++) {
            assertThrows(AppException.class, () ->
                    authService.verifyAdminPassword(adminUser, "wrong", clientIp));
        }

        // 3rd attempt succeeds with valid ADMIN
        Authentication mockAuth = mock(Authentication.class);
        Role adminRole = Role.builder().id(1L).name("ROLE_ADMIN").build();
        Employee adminEmp = Employee.builder().id(1L).username(adminUser).role(adminRole).isActive(true).build();
        when(mockAuth.getPrincipal()).thenReturn(new CustomUserDetails(adminEmp));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(mockAuth);

        assertDoesNotThrow(() -> authService.verifyAdminPassword(adminUser, "correct_pass", clientIp));

        // Now subsequent failure should start count from 1 instead of locking out
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        AppException ex = assertThrows(AppException.class, () ->
                authService.verifyAdminPassword(adminUser, "wrong_again", clientIp));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
    }

    @Test
    @DisplayName("verifyAdminPassword for non-admin user throws ACCESS_DENIED and increments failed attempts")
    void verifyAdminPasswordNonAdminThrowsAccessDeniedAndIncrementsFailures() {
        Authentication mockAuth = mock(Authentication.class);
        Role staffRole = Role.builder().id(2L).name("ROLE_RECEPTION").build();
        Employee staffEmp = Employee.builder().id(2L).username("staff_user").role(staffRole).isActive(true).build();
        when(mockAuth.getPrincipal()).thenReturn(new CustomUserDetails(staffEmp));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(mockAuth);

        String clientIp = "10.0.0.70";
        String username = "staff_user";

        // 3 non-admin attempts should each throw ACCESS_DENIED
        for (int i = 1; i <= 3; i++) {
            AppException ex = assertThrows(AppException.class, () ->
                    authService.verifyAdminPassword(username, "staff_pass", clientIp));
            assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        }

        // 4th attempt should be blocked by RATE_LIMIT_EXCEEDED before calling authenticationManager
        AppException rateLimitEx = assertThrows(AppException.class, () ->
                authService.verifyAdminPassword(username, "staff_pass", clientIp));
        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, rateLimitEx.getErrorCode());
    }

    @Test
    @DisplayName("Failed verifyAdminPassword attempts also lock out standard login endpoint")
    void failedVerifyAdminPasswordLocksOutLogin() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        String clientIp = "10.0.0.80";
        String adminUser = "targeted_admin";

        // 3 failed attempts on secondary admin verification
        for (int i = 1; i <= 3; i++) {
            assertThrows(AppException.class, () ->
                    authService.verifyAdminPassword(adminUser, "guess", clientIp));
        }

        // Login attempt with that user/IP must now be locked out
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(adminUser);
        loginRequest.setPassword("any_password");

        AppException rateLimitEx = assertThrows(AppException.class, () ->
                authService.login(loginRequest, clientIp));
        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, rateLimitEx.getErrorCode());
    }
}
