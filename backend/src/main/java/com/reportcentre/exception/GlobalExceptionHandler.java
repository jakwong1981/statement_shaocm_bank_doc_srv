package com.reportcentre.exception;

import com.reportcentre.service.SystemErrorLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final SystemErrorLogService errorLogService;

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex,
                                                               HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        errorLogService.logException(ex, status, request);
        return buildResponse(status, ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex,
                                                                  HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        errorLogService.logException(ex, status, request);
        return buildResponse(status, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex,
                                                                    HttpServletRequest request) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        errorLogService.logException(ex, status, request);
        return buildResponse(status, "Access denied");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex,
                                                              HttpServletRequest request) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        errorLogService.logException(ex, status, request);
        return buildResponse(status, "Internal server error");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", "ERROR",
                "message", message,
                "code", status.value(),
                "timestamp", Instant.now().toString()
        ));
    }
}
