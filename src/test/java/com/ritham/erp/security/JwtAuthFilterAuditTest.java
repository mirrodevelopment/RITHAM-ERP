package com.ritham.erp.security;

import com.ritham.erp.common.constants.AppConstants;
import com.ritham.erp.module.branch.entity.Branch;
import com.ritham.erp.module.employee.entity.Employee;
import com.ritham.erp.module.employee.entity.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterAuditTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        BranchContext.clear();
        jwtAuthFilter = new JwtAuthFilter(jwtService, userDetailsService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        BranchContext.clear();
    }

    @Test
    @DisplayName("Admin with valid X-Branch-Id sets target BranchContext and logs audit event")
    void adminWithValidBranchHeaderSetsTargetBranch() throws ServletException, IOException {
        String token = "valid.jwt.token";
        when(request.getHeader(AppConstants.JWT_HEADER)).thenReturn(AppConstants.JWT_PREFIX + token);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/orders");
        when(request.getHeader("X-Branch-Id")).thenReturn("2");

        when(jwtService.extractUsername(token)).thenReturn("admin");
        when(jwtService.isAccessToken(token)).thenReturn(true);

        Role adminRole = Role.builder().id(1L).name("ROLE_ADMIN").build();
        Employee adminEmployee = Employee.builder()
                .id(1L)
                .username("admin")
                .role(adminRole)
                .branch(null) // Global admin
                .isActive(true)
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(adminEmployee);

        when(userDetailsService.loadUserByUsername("admin")).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);

        AtomicReference<Long> capturedBranchId = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedBranchId.set(BranchContext.getBranchId());
            return null;
        }).when(filterChain).doFilter(request, response);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertEquals(2L, capturedBranchId.get(), "BranchContext should be set to target branch 2 during request execution");
        assertNull(BranchContext.getBranchId(), "BranchContext should be cleared after request execution completes");
    }

    @Test
    @DisplayName("Admin with X-Branch-Id='ALL' sets BranchContext to null (global scope)")
    void adminWithAllBranchHeaderSetsNullBranchContext() throws ServletException, IOException {
        String token = "valid.jwt.token";
        when(request.getHeader(AppConstants.JWT_HEADER)).thenReturn(AppConstants.JWT_PREFIX + token);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/orders");
        when(request.getHeader("X-Branch-Id")).thenReturn("ALL");

        when(jwtService.extractUsername(token)).thenReturn("admin");
        when(jwtService.isAccessToken(token)).thenReturn(true);

        Role adminRole = Role.builder().id(1L).name("ROLE_ADMIN").build();
        Employee adminEmployee = Employee.builder()
                .id(1L)
                .username("admin")
                .role(adminRole)
                .branch(null)
                .isActive(true)
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(adminEmployee);

        when(userDetailsService.loadUserByUsername("admin")).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);

        AtomicReference<Long> capturedBranchId = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedBranchId.set(BranchContext.getBranchId());
            return null;
        }).when(filterChain).doFilter(request, response);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertNull(capturedBranchId.get(), "BranchContext should be null for ALL branches");
        assertNull(BranchContext.getBranchId(), "BranchContext should be cleared after request");
    }

    @Test
    @DisplayName("Admin with malformed X-Branch-Id defaults to null and logs warning")
    void adminWithMalformedBranchHeaderDefaultsToNull() throws ServletException, IOException {
        String token = "valid.jwt.token";
        when(request.getHeader(AppConstants.JWT_HEADER)).thenReturn(AppConstants.JWT_PREFIX + token);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/orders");
        when(request.getHeader("X-Branch-Id")).thenReturn("NOT_A_NUMBER");

        when(jwtService.extractUsername(token)).thenReturn("admin");
        when(jwtService.isAccessToken(token)).thenReturn(true);

        Role adminRole = Role.builder().id(1L).name("ROLE_ADMIN").build();
        Employee adminEmployee = Employee.builder()
                .id(1L)
                .username("admin")
                .role(adminRole)
                .branch(null)
                .isActive(true)
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(adminEmployee);

        when(userDetailsService.loadUserByUsername("admin")).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);

        AtomicReference<Long> capturedBranchId = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedBranchId.set(BranchContext.getBranchId());
            return null;
        }).when(filterChain).doFilter(request, response);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertNull(capturedBranchId.get(), "BranchContext should default to null on malformed header");
    }

    @Test
    @DisplayName("Non-admin user cannot switch branch via X-Branch-Id and triggers security alert")
    void nonAdminUserCannotSwitchBranchAndTriggersAlert() throws ServletException, IOException {
        String token = "valid.jwt.token";
        when(request.getHeader(AppConstants.JWT_HEADER)).thenReturn(AppConstants.JWT_PREFIX + token);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/orders");
        when(request.getHeader("X-Branch-Id")).thenReturn("2"); // Attempting to spoof branch 2

        when(jwtService.extractUsername(token)).thenReturn("reception_cbe");
        when(jwtService.isAccessToken(token)).thenReturn(true);

        Branch cbeBranch = Branch.builder().id(1L).branchCode("CBE").name("Coimbatore").build();
        Role receptionRole = Role.builder().id(3L).name("ROLE_RECEPTIONIST").build();
        Employee staffEmployee = Employee.builder()
                .id(10L)
                .username("reception_cbe")
                .role(receptionRole)
                .branch(cbeBranch) // Assigned to Branch 1
                .isActive(true)
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(staffEmployee);

        when(userDetailsService.loadUserByUsername("reception_cbe")).thenReturn(userDetails);
        when(jwtService.isTokenValid(token, userDetails)).thenReturn(true);

        AtomicReference<Long> capturedBranchId = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedBranchId.set(BranchContext.getBranchId());
            return null;
        }).when(filterChain).doFilter(request, response);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertEquals(1L, capturedBranchId.get(), "BranchContext must remain locked to staff assigned branch 1");
        assertNull(BranchContext.getBranchId(), "BranchContext should be cleared after request");
    }
}
