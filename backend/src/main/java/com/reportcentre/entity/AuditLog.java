package com.reportcentre.entity;

import com.reportcentre.entity.enums.ActorType;
import com.reportcentre.entity.enums.AuditAction;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "AUDIT_LOGS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "ACTOR_TYPE", nullable = false, length = 20)
    private ActorType actorType;

    @Column(name = "ACTOR_ID", nullable = false, length = 36)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuditAction action;

    @Column(name = "TARGET_REPORT_ID", length = 36)
    private String targetReportId;

    @Column(name = "IP_ADDRESS", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "USER_AGENT", length = 512)
    private String userAgent;

    @Column(name = "REQUEST_DETAILS", columnDefinition = "CLOB")
    private String requestDetails;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
