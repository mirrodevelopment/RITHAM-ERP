package com.ritham.erp.module.order;

import com.ritham.erp.common.exception.AppException;
import com.ritham.erp.common.exception.ErrorCode;
import com.ritham.erp.common.response.ApiResponse;
import com.ritham.erp.module.auth.AuthService;
import com.ritham.erp.module.order.controller.OrderController;
import com.ritham.erp.module.order.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderArchiveSecurityTest {

    @Mock
    private OrderService orderService;

    @Mock
    private AuthService authService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private OrderController orderController;

    @Test
    @DisplayName("OrderController.archiveDeliveredOrders must be annotated with @PreAuthorize(\"hasRole('ADMIN')\")")
    void archiveDeliveredOrdersProtectedByAdminRoleAnnotation() throws NoSuchMethodException {
        Method method = OrderController.class.getMethod("archiveDeliveredOrders", Map.class, HttpServletRequest.class);
        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuth, "archiveDeliveredOrders must have @PreAuthorize annotation");
        assertEquals("hasRole('ADMIN')", preAuth.value(), "archiveDeliveredOrders must be restricted to ADMIN role only");
    }

    @Test
    @DisplayName("archiveDeliveredOrders with valid credentials passes client IP to rate-limited verifyAdminPassword and calls orderService")
    void archiveDeliveredOrdersSuccessWithValidAdminCredentials() {
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.195, 198.51.100.1");
        when(orderService.archiveDeliveredOrders(45)).thenReturn(12);

        Map<String, String> body = new HashMap<>();
        body.put("adminUsername", "admin");
        body.put("password", "valid_secret");
        body.put("keepDays", "45");

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                orderController.archiveDeliveredOrders(body, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());

        // Verify that authService was called with the forwarded client IP
        verify(authService).verifyAdminPassword("admin", "valid_secret", "203.0.113.195");
        // Verify that archive service was called with specified keepDays
        verify(orderService).archiveDeliveredOrders(45);
    }

    @Test
    @DisplayName("archiveDeliveredOrders extracts remoteAddr when X-Forwarded-For is missing")
    void archiveDeliveredOrdersFallsBackToRemoteAddr() {
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.100");
        when(orderService.archiveDeliveredOrders(30)).thenReturn(5);

        Map<String, String> body = Map.of(
                "adminUsername", "admin",
                "password", "secret"
        );

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                orderController.archiveDeliveredOrders(body, httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(authService).verifyAdminPassword("admin", "secret", "192.168.1.100");
        verify(orderService).archiveDeliveredOrders(30);
    }

    @Test
    @DisplayName("archiveDeliveredOrders fails fast with 400 Bad Request when credentials are missing")
    void archiveDeliveredOrdersRejectsMissingCredentials() {
        Map<String, String> body = Map.of(
                "adminUsername", "admin"
                // missing password
        );

        ResponseEntity<ApiResponse<Map<String, Object>>> response =
                orderController.archiveDeliveredOrders(body, httpRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(authService);
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("archiveDeliveredOrders does not proceed if rate limit exceeded in verifyAdminPassword")
    void archiveDeliveredOrdersAbortsOnRateLimitExceeded() {
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.50");

        doThrow(new AppException(ErrorCode.RATE_LIMIT_EXCEEDED))
                .when(authService).verifyAdminPassword("admin", "any_password", "192.168.1.50");

        Map<String, String> body = Map.of(
                "adminUsername", "admin",
                "password", "any_password"
        );

        AppException ex = assertThrows(AppException.class, () ->
                orderController.archiveDeliveredOrders(body, httpRequest));

        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, ex.getErrorCode());
        verifyNoInteractions(orderService);
    }
}
