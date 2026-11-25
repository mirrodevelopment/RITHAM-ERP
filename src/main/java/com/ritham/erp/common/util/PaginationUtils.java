package com.ritham.erp.common.util;

import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Shared helper for constructing validated, bounded {@link Pageable} instances.
 * Prevents arbitrary {@code sortBy} property injection (which triggers HTTP 500
 * {@code PropertyReferenceException}) and oversized page requests.
 */
public final class PaginationUtils {

    /**
     * Upper bound large enough for frontend workstation tables (250-500) and
     * client-side Excel exports (2000), while blocking unbounded queries.
     */
    public static final int MAX_UNBOUNDED_SAFE_PAGE_SIZE = 2000;

    public static final Set<String> ORDER_SORT_FIELDS = Set.of(
            "id", "orderNumber", "customerMobile", "orderDate", "deliveryDate",
            "status", "paymentStatus", "paymentMode", "totalAmount", "advanceAmount",
            "paidAmount", "discountAmount", "assignedEmployeeName", "garmentType",
            "lining", "source", "receiverName", "createdAt", "updatedAt"
    );

    public static final Set<String> EMPLOYEE_SORT_FIELDS = Set.of(
            "id", "employeeCode", "fullName", "mobileNumber", "email", "username",
            "stage", "advance", "joiningDate", "isActive", "createdAt", "updatedAt"
    );

    public static final Set<String> CUSTOMER_SORT_FIELDS = Set.of(
            "customerMobile", "customerName", "createdAt", "totalOrders"
    );

    public static final Set<String> MEASUREMENT_SORT_FIELDS = Set.of(
            "id", "customerMobile", "customerName", "garmentType", "lining",
            "createdAt", "updatedAt"
    );

    private PaginationUtils() {
    }

    public static Pageable safePageRequest(
            int page,
            int size,
            String sortBy,
            String sortDir,
            Set<String> allowedSortFields,
            String defaultSortField
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, MAX_UNBOUNDED_SAFE_PAGE_SIZE));

        String field = (sortBy != null && !sortBy.isBlank()) ? sortBy.trim() : defaultSortField;
        if (allowedSortFields != null && !allowedSortFields.contains(field)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Invalid sort field: " + field);
        }

        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(field).ascending()
                : Sort.by(field).descending();

        return PageRequest.of(safePage, safeSize, sort);
    }
}
