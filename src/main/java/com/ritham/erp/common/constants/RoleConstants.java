package com.ritham.erp.common.constants;

/**
 * Spring Security role name constants.
 * Prefix ROLE_ is required by Spring Security for hasRole() checks.
 */
public final class RoleConstants {

    private RoleConstants() {}

    public static final String ADMIN               = "ROLE_ADMIN";
    public static final String OPERATIONS_MANAGER  = "ROLE_OPERATIONS_MANAGER";
    public static final String PRODUCTION_EMPLOYEE = "ROLE_PRODUCTION_EMPLOYEE";
    public static final String RECEPTION           = "ROLE_RECEPTION";
}
