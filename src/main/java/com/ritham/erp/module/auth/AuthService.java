package com.ritham.erp.module.auth;

import com.ritham.erp.common.config.AppProperties;
import com.ritham.erp.common.constants.AppConstants;
import com.ritham.erp.common.constants.RoleConstants;
import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.module.auth.dto.LoginRequest;
import com.ritham.erp.module.auth.dto.LoginResponse;
import com.ritham.erp.module.auth.dto.RefreshTokenRequest;
import com.ritham.erp.module.auth.entity.RefreshToken;
import com.ritham.erp.module.auth.repository.RefreshTokenRepository;
import com.ritham.erp.module.employee.entity.Employee;
import com.ritham.erp.module.employee.repository.EmployeeRepository;
import com.ritham.erp.security.CustomUserDetails;
import com.ritham.erp.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Business logic for authentication operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager    authenticationManager;
    private final JwtService               jwtService;
    private final RefreshTokenRepository   refreshTokenRepository;
    private final EmployeeRepository       employeeRepository;
    private final AppProperties            appProperties;
    private final LoginRateLimiter         loginRateLimiter;

    // ── Login ─────────────────────────────────────────────────────────────────

    @Transactional
    public LoginResponse login(LoginRequest request) {
        return login(request, AppConstants.DEFAULT_CLIENT_IP);
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String clientIp) {
        // Enforce rate limiting before performing authentication checks
        loginRateLimiter.checkRateLimit(clientIp, request.getUsername());

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );
        } catch (DisabledException e) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        } catch (BadCredentialsException e) {
            loginRateLimiter.recordFailedAttempt(clientIp, request.getUsername());
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        // Clear failed attempt tracking on successful login
        loginRateLimiter.recordSuccessfulLogin(clientIp, request.getUsername());

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Employee employee = userDetails.getEmployee();
        String userIdentifier = employee.getUsername() != null ? employee.getUsername() : employee.getMobileNumber();

        String accessToken  = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userIdentifier);

        // Persist refresh token
        saveRefreshToken(employee, refreshToken);

        log.info("Employee '{}' ({}) logged in successfully", employee.getFullName(), userIdentifier);

        return buildLoginResponse(employee, accessToken, refreshToken);
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Transactional
    public LoginResponse refresh(RefreshTokenRequest request) {
        String rawToken = request.getRefreshToken();

        RefreshToken storedToken = refreshTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new AppException(ErrorCode.TOKEN_INVALID));

        if (!storedToken.isValid()) {
            throw new AppException(ErrorCode.TOKEN_EXPIRED);
        }

        // Rotate: revoke old token, issue new pair
        storedToken.setIsRevoked(true);
        refreshTokenRepository.save(storedToken);

        Employee employee    = storedToken.getEmployee();
        CustomUserDetails ud = new CustomUserDetails(employee);
        String userIdentifier = employee.getUsername() != null ? employee.getUsername() : employee.getMobileNumber();

        String newAccessToken  = jwtService.generateAccessToken(ud);
        String newRefreshToken = jwtService.generateRefreshToken(userIdentifier);

        saveRefreshToken(employee, newRefreshToken);

        log.info("Tokens refreshed for employee '{}' ({})", employee.getFullName(), userIdentifier);

        return buildLoginResponse(employee, newAccessToken, newRefreshToken);
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Transactional
    public void logout(Long employeeId) {
        refreshTokenRepository.revokeAllByEmployeeId(employeeId);
        log.info("Employee id={} logged out — all refresh tokens revoked", employeeId);
    }

    // ── Current user profile ──────────────────────────────────────────────────

    public Employee getCurrentEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));
    }

    // ── Admin password verification (for sensitive operations) ───────────────

    /**
     * Verifies that the supplied username/password match an active ADMIN account.
     * Delegates to {@link #verifyAdminPassword(String, String, String)} with default localhost IP.
     */
    public void verifyAdminPassword(String username, String password) {
        verifyAdminPassword(username, password, AppConstants.DEFAULT_CLIENT_IP);
    }

    /**
     * Verifies that the supplied username/password match an active ADMIN account,
     * enforcing rate-limiting per client IP and username.
     * Throws AppException(INVALID_CREDENTIALS) if wrong password or disabled account.
     * Throws AppException(ACCESS_DENIED) if the account exists but is not ADMIN.
     * Throws AppException(RATE_LIMIT_EXCEEDED) if too many failed attempts occurred.
     */
    public void verifyAdminPassword(String username, String password, String clientIp) {
        // Enforce rate limiting before performing authentication checks
        loginRateLimiter.checkRateLimit(clientIp, username);

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
            CustomUserDetails ud = (CustomUserDetails) auth.getPrincipal();
            String roleName = ud.getEmployee().getRole().getName();
            if (!RoleConstants.ADMIN.equals(roleName) && !"ADMIN".equals(roleName)) {
                loginRateLimiter.recordFailedAttempt(clientIp, username);
                throw new AppException(ErrorCode.ACCESS_DENIED);
            }
            // Clear failed attempt tracking on successful admin verification
            loginRateLimiter.recordSuccessfulLogin(clientIp, username);
        } catch (BadCredentialsException | DisabledException e) {
            loginRateLimiter.recordFailedAttempt(clientIp, username);
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
    }


    // ── Helpers ───────────────────────────────────────────────────────────────

    private void saveRefreshToken(Employee employee, String token) {
        long expiryMs = appProperties.getJwt().getRefreshTokenExpiryMs();
        RefreshToken rt = RefreshToken.builder()
                .employee(employee)
                .token(token)
                .expiresAt(LocalDateTime.now().plusSeconds(expiryMs / 1000))
                .build();
        refreshTokenRepository.save(rt);
    }

    private LoginResponse buildLoginResponse(
            Employee employee, String accessToken, String refreshToken) {
        com.ritham.erp.module.branch.entity.Branch branch = employee.getBranch();
        boolean isGlobalAdmin = RoleConstants.ADMIN.equals(employee.getRole().getName()) && branch == null;

        return LoginResponse.builder()
                .employeeId(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .fullName(employee.getFullName())
                .username(employee.getUsername() != null ? employee.getUsername() : employee.getMobileNumber())
                .role(employee.getRole().getName())
                .branchId(branch != null ? branch.getId() : null)
                .branchName(branch != null ? branch.getName() : (isGlobalAdmin ? "All Branches (Global)" : null))
                .branchCode(branch != null ? branch.getBranchCode() : (isGlobalAdmin ? "GLOBAL" : null))
                .isGlobalAdmin(isGlobalAdmin)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresInMs(appProperties.getJwt().getAccessTokenExpiryMs())
                .build();
    }
}
