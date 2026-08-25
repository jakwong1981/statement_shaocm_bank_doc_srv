package com.reportcentre.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "SYSTEM_ERROR_LOGS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SystemErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ERROR_MESSAGE", nullable = false, length = 2000)
    private String errorMessage;

    @Column(name = "EXCEPTION_CLASS", nullable = false, length = 500)
    private String exceptionClass;

    @Column(name = "STACK_TRACE", columnDefinition = "CLOB")
    private String stackTrace;

    @Column(name = "HTTP_METHOD", length = 10)
    private String httpMethod;

    @Column(name = "REQUEST_URI", length = 1000)
    private String requestUri;

    @Column(name = "HTTP_STATUS")
    private Integer httpStatus;

    @Column(name = "USER_AGENT", length = 512)
    private String userAgent;

    @Column(name = "IP_ADDRESS", length = 45)
    private String ipAddress;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
