package com.ritham.erp.security;

/**
 * ThreadLocal context holding the active branch ID for the current request.
 * Allows global System Administrator to switch branch context via the X-Branch-Id header,
 * while branch staff are automatically scoped to their assigned branch.
 */
public final class BranchContext {

    private static final ThreadLocal<Long> CURRENT_BRANCH_ID = new ThreadLocal<>();

    private BranchContext() {}

    public static void setBranchId(Long branchId) {
        CURRENT_BRANCH_ID.set(branchId);
    }

    public static Long getBranchId() {
        return CURRENT_BRANCH_ID.get();
    }

    public static void clear() {
        CURRENT_BRANCH_ID.remove();
    }
}
