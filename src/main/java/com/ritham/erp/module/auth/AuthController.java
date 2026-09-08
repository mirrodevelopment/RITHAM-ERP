package com.ritham.erp.module.auth;

import com.ritham.erp.common.constants.AppConstants;
import com.ritham.erp.common.constants.RoleConstants;
import com.ritham.erp.common.response.ApiResponse;
import com.ritham.erp.module.auth.dto.LoginRequest;
import com.ritham.erp.module.auth.dto.LoginResponse;
import com.ritham.erp.module.auth.dto.RefreshTokenRequest;
import com.ritham.erp.module.employee.entity.Employee;
import com.ritham.erp.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication REST controller.
 *
 * <pre>
 * POST   /auth/login      — authenticate and receive token pair
 * POST   /auth/refresh    — exchange refresh token for new pair
 * POST   /auth/logout     — revoke all refresh tokens
 * GET    /auth/me         — current employee profile
 * </pre>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Login endpoint — open (no authentication required).
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        String clientIp = extractClientIp(httpRequest);
        LoginResponse response = authService.login(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    private String extractClientIp(jakarta.servlet.http.HttpServletRequest request) {
        if (request == null) {
            return AppConstants.DEFAULT_CLIENT_IP;
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : AppConstants.DEFAULT_CLIENT_IP;
    }

    /**
     * Refresh token endpoint — open (uses refresh token to authenticate).
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {

        LoginResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed", response));
    }

    /**
     * Logout — revokes all refresh tokens for the current employee.
     * Requires a valid access token.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        authService.logout(currentUser.getEmployeeId());
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    /**
     * Current employee profile — requires a valid access token.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentUser(
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        Employee employee = authService.getCurrentEmployee(currentUser.getEmployeeId());
        com.ritham.erp.module.branch.entity.Branch branch = employee.getBranch();
        boolean isGlobalAdmin = RoleConstants.ADMIN.equals(employee.getRole().getName()) && branch == null;

        Map<String, Object> profile = new java.util.HashMap<>();
        profile.put("id",           employee.getId());
        profile.put("employeeCode", employee.getEmployeeCode());
        profile.put("fullName",     employee.getFullName());
        profile.put("username",     employee.getUsername() != null ? employee.getUsername() : employee.getMobileNumber());
        profile.put("email",        employee.getEmail() != null ? employee.getEmail() : "");
        profile.put("mobileNumber", employee.getMobileNumber());
        profile.put("role",         employee.getRole().getName());
        profile.put("isActive",     employee.getIsActive());
        profile.put("branchId",     branch != null ? branch.getId() : null);
        profile.put("branchName",   branch != null ? branch.getName() : (isGlobalAdmin ? "All Branches (Global)" : null));
        profile.put("branchCode",   branch != null ? branch.getBranchCode() : (isGlobalAdmin ? "GLOBAL" : null));
        profile.put("isGlobalAdmin", isGlobalAdmin);

        return ResponseEntity.ok(ApiResponse.success(profile));
    }
}
