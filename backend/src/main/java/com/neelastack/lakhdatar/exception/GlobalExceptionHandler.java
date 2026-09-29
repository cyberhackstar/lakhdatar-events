package com.neelastack.lakhdatar.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message){
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), code, message, MDC.get("correlationId")));
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handle(ApiException ex){ return error(ex.status(), ex.code(), ex.getMessage()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex){
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage).filter(m->m!=null).distinct().collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message.isBlank()?"Invalid request":message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex){ return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage()); }

    @ExceptionHandler({org.springframework.web.method.annotation.HandlerMethodValidationException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            jakarta.validation.ConstraintViolationException.class})
    ResponseEntity<ApiError> handleBadInput(Exception ex){ return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request parameters"); }

    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.HttpRequestMethodNotSupportedException.class})
    ResponseEntity<ApiError> handleNotFound(Exception ex){
        if (ex instanceof org.springframework.web.HttpRequestMethodNotSupportedException) return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed");
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnknown(Exception ex){
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong. Reference the correlation ID for support.");
    }
}
