package com.ritham.erp.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Enumeration of all application error codes.
 * Each code carries an HTTP status and a human-readable message.
 */
@Getter
public enum ErrorCode {

    // ── Generic ──────────────────────────────────────────────────────────────
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Input validation failed"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "Resource already exists"),
    OPERATION_NOT_ALLOWED(HttpStatus.FORBIDDEN, "Operation not allowed"),

    // ── Authentication ────────────────────────────────────────────────────────
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid username or password"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token has expired"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Token is invalid"),
    TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "Authentication token is missing"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have permission to perform this action"),
    ACCOUNT_DISABLED(HttpStatus.UNAUTHORIZED, "Account is disabled"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Too many failed login attempts. Please wait 15 minutes before trying again."),

    // ── Employee ──────────────────────────────────────────────────────────────
    EMPLOYEE_NOT_FOUND(HttpStatus.NOT_FOUND, "Employee not found"),
    EMPLOYEE_USERNAME_TAKEN(HttpStatus.CONFLICT, "Username is already taken"),
    EMPLOYEE_MOBILE_TAKEN(HttpStatus.CONFLICT, "Mobile number is already registered"),

    // ── Customer ──────────────────────────────────────────────────────────────
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "Customer not found"),
    CUSTOMER_ALREADY_EXISTS(HttpStatus.CONFLICT, "Customer with this mobile number already exists"),

    // ── Order ─────────────────────────────────────────────────────────────────
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Order not found"),
    ORDER_CANNOT_BE_CANCELLED(HttpStatus.BAD_REQUEST, "Order cannot be cancelled in its current state"),
    INVALID_ORDER_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "Invalid order status transition"),

    // ── Concurrency ───────────────────────────────────────────────────────────
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Order was modified by another user. Please refresh and try again.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message    = message;
    }
}
