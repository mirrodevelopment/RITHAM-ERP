package com.ritham.erp.module.order.controller;

import com.ritham.erp.common.constants.AppConstants;
import com.ritham.erp.common.response.ApiResponse;
import com.ritham.erp.common.response.PageResponse;
import com.ritham.erp.module.order.dto.CreateOrderRequest;
import com.ritham.erp.module.order.dto.OrderResponse;
import com.ritham.erp.module.order.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.ritham.erp.module.order.dto.OrderPaymentResponse;
import com.ritham.erp.module.order.dto.RecordPaymentRequest;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final com.ritham.erp.module.auth.AuthService authService;

    /** GET /api/orders — paginated, searchable list */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getOrders(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")    int page,
            @RequestParam(defaultValue = "20")   int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrders(search, status, pageable)));
    }

    /** GET /api/orders/stats — summary counts for dashboard */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "total",        orderService.countTotalOrders(),
                "pending",      orderService.countPendingOrders(),
                "inProgress",   orderService.countInProgressOrders(),
                "completed",    orderService.countCompletedOrders(),
                "cancelled",    orderService.countCancelledOrders(),
                "todayOrders",  orderService.countTodayOrders(),
                "todayRevenue", orderService.sumTodayRevenue()
        )));
    }

    /** GET /api/orders/revenue — query revenue by date range, branch, and payment method */
    @GetMapping("/revenue")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRevenueReport(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false) Long branchId
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getRevenueReport(startDate, endDate, paymentMethod, branchId)));
    }

    /** GET /api/orders/analytics — server-side aggregation for reports & analytics desk */
    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrderAnalytics(
            @RequestParam(defaultValue = "WEEK") String preset,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String paymentMode,
            @RequestParam(required = false) String searchQuery,
            @RequestParam(required = false) Long branchId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.getOrderAnalytics(preset, startDate, endDate, stage, paymentStatus, paymentMode, searchQuery, branchId)
        ));
    }

    /** GET /api/orders/delivered/archive-stats — preview stats for archive cleanup */
    @GetMapping("/delivered/archive-stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDeliveredArchiveStats(
            @RequestParam(defaultValue = "30") int keepDays
    ) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getDeliveredArchiveStats(keepDays)));
    }

    /** POST /api/orders/delivered/archive — delete old delivered orders (requires admin credentials) */
    @PostMapping("/delivered/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> archiveDeliveredOrders(
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest
    ) {
        String username = body.get("adminUsername");
        String password = body.get("password");
        int keepDays = 30;
        if (body.containsKey("keepDays") && body.get("keepDays") != null) {
            try { keepDays = Integer.parseInt(body.get("keepDays")); }
            catch (NumberFormatException ignored) {}
        }

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Map<String, Object>>errorOf("Admin username and password are required"));
        }

        String clientIp = extractClientIp(httpRequest);

        // Verify password against Admin account with rate limiting
        authService.verifyAdminPassword(username, password, clientIp);

        // Perform archive delete
        int deletedCount = orderService.archiveDeliveredOrders(keepDays);

        return ResponseEntity.ok(ApiResponse.success(
                "Archive cleanup completed successfully",
                Map.of("deletedCount", deletedCount, "keepDays", keepDays)
        ));
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return AppConstants.DEFAULT_CLIENT_IP;
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : AppConstants.DEFAULT_CLIENT_IP;
    }

    /** GET /api/orders/customer/{mobile}/latest-measurements — latest measurements for repeat customer */
    @GetMapping("/customer/{mobile}/latest-measurements")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLatestCustomerMeasurements(@PathVariable String mobile) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getLatestCustomerMeasurements(mobile)));
    }

    /** GET /api/orders/customer/{mobile}/due — customer outstanding due summary */
    @GetMapping("/customer/{mobile}/due")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCustomerDue(@PathVariable String mobile) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getCustomerDueSummary(mobile)));
    }

    /** GET /api/orders/{id} — single order */
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderById(id)));
    }

    /** POST /api/orders — create new order */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request
    ) {
        OrderResponse created = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order created successfully", created));
    }

    /** PUT /api/orders/{id}/status — update order status (+ optional final payment / employee assignment) */
    @PutMapping("/{id:\\d+}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        String newStatus = body.get("status") != null ? body.get("status").toString() : null;
        String paymentMode = body.get("paymentMode") != null ? body.get("paymentMode").toString() : null;
        String assignedEmployeeName = body.get("assignedEmployeeName") != null ? body.get("assignedEmployeeName").toString() : null;
        String receiverName = body.get("receiverName") != null ? body.get("receiverName").toString() : null;
        
        Long assignedEmployeeId = null;
        if (body.get("assignedEmployeeId") != null && !body.get("assignedEmployeeId").toString().isBlank()) {
            try { assignedEmployeeId = Long.parseLong(body.get("assignedEmployeeId").toString()); }
            catch (NumberFormatException ignored) {}
        }

        java.math.BigDecimal finalPayment = null;
        if (body.containsKey("finalPayment") && body.get("finalPayment") != null
                && !body.get("finalPayment").toString().isBlank()) {
            try { finalPayment = new java.math.BigDecimal(body.get("finalPayment").toString()); }
            catch (NumberFormatException ignored) {}
        }

        java.math.BigDecimal discountAmount = null;
        if (body.containsKey("discountAmount") && body.get("discountAmount") != null
                && !body.get("discountAmount").toString().isBlank()) {
            try { discountAmount = new java.math.BigDecimal(body.get("discountAmount").toString()); }
            catch (NumberFormatException ignored) {}
        }

        return ResponseEntity.ok(ApiResponse.success(
                "Status updated", orderService.updateStatus(id, newStatus, finalPayment, paymentMode, assignedEmployeeId, assignedEmployeeName, receiverName, discountAmount)));
    }

    /** DELETE /api/orders/{id}/cancel — cancel order */
    @DeleteMapping("/{id:\\d+}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Order cancelled", orderService.cancelOrder(id)));
    }

    /** GET /api/orders/{id}/payments — get all ledger payments recorded for an order */
    @GetMapping("/{id:\\d+}/payments")
    public ResponseEntity<ApiResponse<List<OrderPaymentResponse>>> getOrderPayments(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderPayments(id)));
    }

    /** POST /api/orders/{id}/payments — record a payment transaction in ledger */
    @PostMapping("/{id:\\d+}/payments")
    public ResponseEntity<ApiResponse<OrderPaymentResponse>> recordPayment(
            @PathVariable Long id,
            @Valid @RequestBody RecordPaymentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment recorded successfully", orderService.recordPayment(id, request)));
    }
}



