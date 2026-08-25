package com.reportcentre.entity;

import com.reportcentre.entity.enums.ClientStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "THIRD_PARTY_CLIENTS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ThirdPartyClient {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "CLIENT_NAME", nullable = false, length = 128)
    private String clientName;

    @Column(name = "API_KEY", nullable = false, unique = true, length = 64)
    private String apiKey;

    @Column(name = "API_SECRET_HASH", nullable = false, length = 255)
    private String apiSecretHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ClientStatus status = ClientStatus.ACTIVE;

    @Column(name = "ALLOWED_IPS", length = 2048)
    @Builder.Default
    private String allowedIps = "";

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
