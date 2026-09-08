package com.ritham.erp.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Application-level runtime exception carrying an {@link ErrorCode}.
 * Throw this anywhere in the service layer; the {@link GlobalExceptionHandler}
 * will map it to the correct HTTP response automatically.
 */
@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode  = errorCode;
        this.httpStatus = errorCode.getHttpStatus();
    }

    public AppException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode  = errorCode;
        this.httpStatus = errorCode.getHttpStatus();
    }

    public AppException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode  = errorCode;
        this.httpStatus = errorCode.getHttpStatus();
    }
}
