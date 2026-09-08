package com.ritham.erp.module.auth.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Response body for login and token-refresh endpoints.
 */
@Getter
@Builder
public class LoginResponse {

    private final Long   employeeId;
    private final String employeeCode;
    private final String fullName;
    private final String username;
    private final String role;
    private final Long   branchId;
    private final String branchName;
    private final String branchCode;
    private final Boolean isGlobalAdmin;

    private final String accessToken;
    private final String refreshToken;
    private final long   accessTokenExpiresInMs;
}
