package com.ritham.erp.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standard envelope for every API response.
 *
 * <pre>
 * Success:  { "success": true,  "message": "...", "data": {...}, "timestamp": "..." }
 * Error:    { "success": false, "message": "...", "errors": [...], "timestamp": "..." }
 * </pre>
 *
 * @param <T> Type of the payload data
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean       success;
    private final String        message;
    private final T             data;
    private final List<String>  errors;

    @Builder.Default
    private final LocalDateTime timestamp = LocalDateTime.now();

    // ── Factory helpers ───────────────────────────────────────────────────────

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(T data) {
        return success("Operation successful", data);
    }

    public static ApiResponse<Void> success(String message) {
        return ApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .build();
    }

    public static ApiResponse<Void> error(String message) {
        return ApiResponse.<Void>builder()
                .success(false)
                .message(message)
                .build();
    }

    public static ApiResponse<Void> error(String message, List<String> errors) {
        return ApiResponse.<Void>builder()
                .success(false)
                .message(message)
                .errors(errors)
                .build();
    }

    /**
     * Generic error factory — use when the caller's return type is
     * {@code ApiResponse<T>} for some concrete T (not Void).
     * Example: {@code ApiResponse.<Map<String,Object>>errorOf("msg")}
     */
    public static <T> ApiResponse<T> errorOf(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }

    public static <T> ApiResponse<T> errorOf(String message, List<String> errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errors(errors)
                .build();
    }
}
