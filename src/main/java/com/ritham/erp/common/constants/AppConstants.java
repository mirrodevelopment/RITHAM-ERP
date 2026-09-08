package com.ritham.erp.common.constants;

/**
 * Global application-wide constants.
 */
public final class AppConstants {

    private AppConstants() {}

    // ── API versioning ──────────────────────────────────────────────────────
    public static final String API_VERSION = "v1";

    // ── JWT ─────────────────────────────────────────────────────────────────
    public static final String JWT_HEADER       = "Authorization";
    public static final String JWT_PREFIX       = "Bearer ";
    public static final String JWT_TOKEN_TYPE   = "access";
    public static final String JWT_REFRESH_TYPE = "refresh";
    public static final String JWT_CLAIM_TYPE   = "tokenType";
    public static final String JWT_CLAIM_ROLE   = "role";

    // ── Pagination defaults ─────────────────────────────────────────────────
    public static final int    DEFAULT_PAGE_SIZE   = 20;
    public static final int    MAX_PAGE_SIZE        = 100;
    public static final String DEFAULT_SORT_FIELD   = "createdAt";
    public static final String DEFAULT_SORT_DIR     = "desc";

    // ── Date formats ─────────────────────────────────────────────────────────
    public static final String DATE_FORMAT          = "yyyy-MM-dd";
    public static final String DATETIME_FORMAT      = "yyyy-MM-dd'T'HH:mm:ss";

    // ── Employee codes ────────────────────────────────────────────────────────
    public static final String EMPLOYEE_CODE_PREFIX = "EMP-";

    // ── Order numbers ─────────────────────────────────────────────────────────
    public static final String ORDER_NUMBER_PREFIX  = "ORD-";

    // ── Network / Client Defaults ───────────────────────────────────────────
    public static final String DEFAULT_CLIENT_IP    = "127.0.0.1";
    public static final String BRANCH_HEADER        = "X-Branch-Id";
}
