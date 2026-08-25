package com.reportcentre.service;

import com.reportcentre.dto.SystemErrorLogResponse;
import com.reportcentre.entity.SystemErrorLog;
import com.reportcentre.repository.SystemErrorLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemErrorLogService {

    private final SystemErrorLogRepository errorLogRepository;

    public void logException(Exception ex, HttpStatus status, HttpServletRequest request) {
        try {
            SystemErrorLog errorLog = SystemErrorLog.builder()
                    .errorMessage(truncate(ex.getMessage(), 2000))
                    .exceptionClass(ex.getClass().getName())
                    .stackTrace(truncate(getStackTrace(ex), 10000))
                    .httpMethod(request.getMethod())
                    .requestUri(truncate(request.getRequestURI(), 1000))
                    .httpStatus(status.value())
                    .userAgent(truncate(request.getHeader("User-Agent"), 512))
                    .ipAddress(getClientIp(request))
                    .build();
            errorLogRepository.save(errorLog);
        } catch (Exception e) {
            log.warn("Failed to persist error log: {}", e.getMessage());
        }
    }

    public Page<SystemErrorLogResponse> getErrorLogs(Instant from, Instant to, Pageable pageable) {
        if (from != null || to != null) {
            return errorLogRepository.findByTimeRange(from, to, pageable).map(this::toResponse);
        }
        return errorLogRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
    }

    private SystemErrorLogResponse toResponse(SystemErrorLog entity) {
        return SystemErrorLogResponse.builder()
                .id(entity.getId())
                .errorMessage(entity.getErrorMessage())
                .exceptionClass(entity.getExceptionClass())
                .stackTrace(entity.getStackTrace())
                .httpMethod(entity.getHttpMethod())
                .requestUri(entity.getRequestUri())
                .httpStatus(entity.getHttpStatus())
                .userAgent(entity.getUserAgent())
                .ipAddress(entity.getIpAddress())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private String getStackTrace(Exception ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return null;
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return truncate(ip, 45);
    }
}
