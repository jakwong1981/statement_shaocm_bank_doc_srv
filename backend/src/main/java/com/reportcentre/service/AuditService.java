package com.reportcentre.service;

import com.reportcentre.dto.AuditLogResponse;
import com.reportcentre.entity.AuditLog;
import com.reportcentre.entity.enums.ActorType;
import com.reportcentre.entity.enums.AuditAction;
import com.reportcentre.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void logAction(ActorType actorType, String actorId, AuditAction action,
                          String targetReportId, String ipAddress, String userAgent,
                          String requestDetails) {
        AuditLog log = AuditLog.builder()
                .actorType(actorType)
                .actorId(actorId)
                .action(action)
                .targetReportId(targetReportId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .requestDetails(requestDetails)
                .build();
        auditLogRepository.save(log);
    }

    public Page<AuditLogResponse> getAuditLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable).map(this::toResponse);
    }

    public Page<AuditLogResponse> getAuditLogsByActor(String actorId, Pageable pageable) {
        return auditLogRepository.findByActorId(actorId, pageable).map(this::toResponse);
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .actorType(log.getActorType().name())
                .actorId(log.getActorId())
                .action(log.getAction().name())
                .targetReportId(log.getTargetReportId())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
