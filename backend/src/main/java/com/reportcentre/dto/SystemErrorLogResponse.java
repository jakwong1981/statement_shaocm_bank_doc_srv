package com.reportcentre.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
public class SystemErrorLogResponse {
    private Long id;
    private String errorMessage;
    private String exceptionClass;
    private String stackTrace;
    private String httpMethod;
    private String requestUri;
    private Integer httpStatus;
    private String userAgent;
    private String ipAddress;
    private Instant createdAt;
}
