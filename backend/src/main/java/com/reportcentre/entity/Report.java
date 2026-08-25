package com.reportcentre.entity;

import com.reportcentre.entity.enums.ReportStatus;
import com.reportcentre.entity.enums.UploaderType;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "REPORTS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Report {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "ORIGINAL_FILENAME", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "FILE_SIZE_BYTES", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "PAGE_COUNT")
    private Integer pageCount;

    @Column(name = "RAW_STORAGE_PATH", nullable = false, length = 512)
    private String rawStoragePath;

    @Column(name = "WATERMARKED_STORAGE_PATH", length = 512)
    private String watermarkedStoragePath;

    @Column(name = "CHECKSUM_SHA256", length = 64)
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING_WATERMARK;

    @Enumerated(EnumType.STRING)
    @Column(name = "UPLOADED_BY_TYPE", nullable = false, length = 20)
    private UploaderType uploadedByType;

    @Column(name = "UPLOADED_BY_ID", nullable = false, length = 36)
    private String uploadedById;

    @Column(name = "BENCHMARK_TAG", length = 100)
    private String benchmarkTag;

    @Column(columnDefinition = "CLOB")
    private String metadata;

    @Column(name = "ERROR_REASON", length = 1024)
    private String errorReason;

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
