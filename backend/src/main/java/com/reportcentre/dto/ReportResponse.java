package com.reportcentre.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
public class ReportResponse {
    private String reportId;
    private String filename;
    private Long fileSizeBytes;
    private Integer pageCount;
    private String status;
    private String benchmarkTag;
    private String checksumSha256;
    private Instant createdAt;
    private Instant watermarkedAt;
    private String errorReason;
}
