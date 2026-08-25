package com.reportcentre.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
public class AuditLogResponse {
    private Long id;
    private String actorType;
    private String actorId;
    private String action;
    private String targetReportId;
    private String ipAddress;
    private String userAgent;
    private Instant createdAt;
}
