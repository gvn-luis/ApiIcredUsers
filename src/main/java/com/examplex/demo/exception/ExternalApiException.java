package com.examplex.demo.exception;

import lombok.Getter;

/**
 * Exceção customizada para erros da API externa
 */
@Getter
public class ExternalApiException extends RuntimeException {

    private final String errorCode;
    private final Integer httpStatus;

    public ExternalApiException(String message) {
        super(message);
        this.errorCode = null;
        this.httpStatus = null;
    }

    public ExternalApiException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = null;
    }

    public ExternalApiException(String message, Integer httpStatus, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public ExternalApiException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
        this.httpStatus = null;
    }

    public boolean isBannedUser() {
        return errorCode != null && "CORE__PREVENT_FOUND".equals(errorCode);
    }

    public boolean isAlreadyExists() {
        return errorCode != null && "ALREADY_EXISTS".equals(errorCode);
    }

    public boolean isAlreadyActive() {
        return errorCode != null && "ALREADY_ACTIVE".equals(errorCode);
    }
}