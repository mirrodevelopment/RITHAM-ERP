package com.ritham.erp.common.exception;

import com.ritham.erp.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Verification and security tests for TASK-033:
 * Sanitizing malformed JSON error responses in GlobalExceptionHandler
 * to prevent internal exception and parser stack trace disclosure.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("TASK-033: handleHttpMessageNotReadable returns generic sanitized message without parser internals")
    void handleHttpMessageNotReadableSanitizesMessage() {
        String sensitiveParserTrace = "Cannot deserialize value of type `java.lang.Long` from String \"invalid\": " +
                "not a valid Long value at [Source: (String)\"{\"branchId\": \"invalid\"}\"; line: 1, column: 15] " +
                "(through reference chain: com.ritham.erp.module.order.dto.CreateOrderRequest[\"branchId\"])";
        Throwable rootCause = new IllegalArgumentException(sensitiveParserTrace);
        HttpInputMessage mockHttpInputMessage = mock(HttpInputMessage.class);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("JSON parse error: " + sensitiveParserTrace, rootCause, mockHttpInputMessage);

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleHttpMessageNotReadable(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());

        // Must match exact sanitized message
        assertEquals("Malformed JSON request payload", response.getBody().getMessage());

        // Must not leak internal class names, field paths, or Jackson details
        assertFalse(response.getBody().getMessage().contains("com.ritham.erp"));
        assertFalse(response.getBody().getMessage().contains("CreateOrderRequest"));
        assertFalse(response.getBody().getMessage().contains("branchId"));
        assertFalse(response.getBody().getMessage().contains("line: 1"));
    }

    @Test
    @DisplayName("TASK-033: handleHttpMessageNotReadable with null cause still returns generic sanitized message")
    void handleHttpMessageNotReadableWithNullCauseReturnsSanitizedMessage() {
        HttpInputMessage mockHttpInputMessage = mock(HttpInputMessage.class);
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Required request body is missing", mockHttpInputMessage);

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleHttpMessageNotReadable(ex);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Malformed JSON request payload", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleAppException returns correct HTTP status and message")
    void handleAppException() {
        AppException ex = new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found with ID 999");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAppException(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Order not found with ID 999", response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleAccessDenied returns 403 Forbidden with standard message")
    void handleAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("Forbidden");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAccessDenied(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.ACCESS_DENIED.getMessage(), response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleAuthenticationException returns 401 Unauthorized with standard message")
    void handleAuthenticationException() {
        AuthenticationException ex = new AuthenticationException("Bad credentials") {};
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleAuthenticationException(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.INVALID_CREDENTIALS.getMessage(), response.getBody().getMessage());
    }

    @Test
    @DisplayName("handleDataIntegrityViolation returns 409 Conflict with generic message")
    void handleDataIntegrityViolation() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("violates foreign key constraint");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleDataIntegrityViolation(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("Operation cannot be completed"));
    }

    @Test
    @DisplayName("handleUnexpected returns 500 Internal Server Error without leaking internal stack trace")
    void handleUnexpected() {
        Exception ex = new NullPointerException("Null reference at com.ritham.erp.internal.Service.execute()");
        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleUnexpected(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), response.getBody().getMessage());
    }
}
