package com.examplex.demo.exception;

import com.examplex.demo.model.dto.ApiResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler global de exceções
 *
 * Centraliza o tratamento de exceções, removendo necessidade de try-catch nos Controllers
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Trata exceções de validação do Bean Validation
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Erro de validação: {}", errors);

        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "success", false,
                        "message", "Erro de validação",
                        "errors", errors
                ));
    }

    /**
     * Trata exceções da API externa
     */
    @ExceptionHandler(ExternalApiException.class)
    public ResponseEntity<ApiResponseDto> handleExternalApiException(ExternalApiException ex) {
        log.error("Erro na API externa: {}", ex.getMessage());

        HttpStatus status = ex.getHttpStatus() != null && ex.getHttpStatus() >= 400 && ex.getHttpStatus() < 500
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.INTERNAL_SERVER_ERROR;

        return ResponseEntity
                .status(status)
                .body(ApiResponseDto.error(ex.getMessage()));
    }

    /**
     * Trata IllegalArgumentException (enums inválidos, etc)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponseDto> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Argumento inválido: {}", ex.getMessage());

        return ResponseEntity
                .badRequest()
                .body(ApiResponseDto.error(ex.getMessage()));
    }

    /**
     * Trata exceções genéricas não tratadas
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto> handleGenericException(Exception ex) {
        log.error("Erro não tratado: {}", ex.getMessage(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseDto.error("Erro interno do servidor"));
    }
}