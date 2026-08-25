package com.reportcentre.service;

import com.reportcentre.dto.ReportResponse;
import com.reportcentre.entity.Report;
import com.reportcentre.entity.enums.ReportStatus;
import com.reportcentre.entity.enums.UploaderType;
import com.reportcentre.exception.BadRequestException;
import com.reportcentre.exception.ResourceNotFoundException;
import com.reportcentre.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final ReportRepository reportRepository;
    private final StorageService storageService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.routing-key}")
    private String routingKey;

    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46}; // %PDF

    public ReportResponse upload(MultipartFile file, String benchmarkTag,
                                  String metadata, UploaderType uploaderType,
                                  String uploaderId) {
        return uploadOrReplace(null, file, benchmarkTag, metadata, uploaderType, uploaderId);
    }

    public ReportResponse uploadOrReplace(String reportId, MultipartFile file, String benchmarkTag,
                                           String metadata, UploaderType uploaderType,
                                           String uploaderId) {
        validatePdf(file);

        // If reportId provided and report exists → replace mode
        if (reportId != null && !reportId.isBlank() && reportRepository.existsById(reportId)) {
            return replaceReport(reportId, file, benchmarkTag, metadata, uploaderType, uploaderId);
        }

        // Create new (use provided reportId or generate new one)
        String newId = (reportId != null && !reportId.isBlank()) ? reportId : UUID.randomUUID().toString();
        return createReport(newId, file, benchmarkTag, metadata, uploaderType, uploaderId);
    }

    private ReportResponse createReport(String reportId, MultipartFile file, String benchmarkTag,
                                         String metadata, UploaderType uploaderType, String uploaderId) {
        String objectName = reportId + ".pdf";

        try {
            storageService.uploadRaw(objectName, file.getInputStream(),
                    file.getSize(), "application/pdf");
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }

        Report report = Report.builder()
                .id(reportId)
                .originalFilename(file.getOriginalFilename())
                .fileSizeBytes(file.getSize())
                .rawStoragePath(objectName)
                .status(ReportStatus.PENDING_WATERMARK)
                .uploadedByType(uploaderType)
                .uploadedById(uploaderId)
                .benchmarkTag(benchmarkTag)
                .metadata(metadata)
                .build();
        reportRepository.save(report);

        publishWatermarkMessage(reportId, objectName, benchmarkTag, uploaderId);
        log.info("Report {} uploaded and queued for watermarking", reportId);
        return toResponse(report);
    }

    private ReportResponse replaceReport(String reportId, MultipartFile file, String benchmarkTag,
                                          String metadata, UploaderType uploaderType, String uploaderId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + reportId));

        // Delete old files from MinIO (best-effort, log warnings on failure)
        if (report.getRawStoragePath() != null) {
            storageService.deleteFromStaging(report.getRawStoragePath());
        }
        if (report.getWatermarkedStoragePath() != null) {
            storageService.deleteFromWatermarked(report.getWatermarkedStoragePath());
        }

        // Upload new file
        String objectName = reportId + ".pdf";
        try {
            storageService.uploadRaw(objectName, file.getInputStream(),
                    file.getSize(), "application/pdf");
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }

        // Update entity
        report.setOriginalFilename(file.getOriginalFilename());
        report.setFileSizeBytes(file.getSize());
        report.setRawStoragePath(objectName);
        report.setWatermarkedStoragePath(null);
        report.setChecksumSha256(null);
        report.setPageCount(null);
        report.setErrorReason(null);
        report.setStatus(ReportStatus.PENDING_WATERMARK);
        report.setUploadedByType(uploaderType);
        report.setUploadedById(uploaderId);
        if (benchmarkTag != null) {
            report.setBenchmarkTag(benchmarkTag);
        }
        if (metadata != null) {
            report.setMetadata(metadata);
        }
        reportRepository.save(report);

        publishWatermarkMessage(reportId, objectName, benchmarkTag, uploaderId);
        log.info("Report {} replaced and re-queued for watermarking", reportId);
        return toResponse(report);
    }

    private void publishWatermarkMessage(String reportId, String objectName,
                                          String benchmarkTag, String uploaderId) {
        rabbitTemplate.convertAndSend(exchange, routingKey,
                Map.of("reportId", reportId,
                       "rawStoragePath", objectName,
                       "benchmarkTag", benchmarkTag != null ? benchmarkTag : "",
                       "uploaderId", uploaderId));
    }

    public ReportResponse getReport(String id) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + id));
        return toResponse(report);
    }

    public Page<ReportResponse> getReports(String status, String benchmarkTag, Pageable pageable) {
        Page<Report> page;
        if (status != null && !status.isBlank()) {
            page = reportRepository.findByStatus(ReportStatus.valueOf(status), pageable);
        } else if (benchmarkTag != null && !benchmarkTag.isBlank()) {
            page = reportRepository.findByBenchmarkTag(benchmarkTag, pageable);
        } else {
            page = reportRepository.findAll(pageable);
        }
        return page.map(this::toResponse);
    }

    public String getDownloadUrl(String id) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + id));
        if (report.getStatus() != ReportStatus.READY) {
            throw new BadRequestException("Report not ready for download. Status: " + report.getStatus());
        }
        return storageService.getPresignedUrl(report.getWatermarkedStoragePath());
    }

    public InputStream downloadReport(String id) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + id));
        if (report.getStatus() != ReportStatus.READY) {
            throw new BadRequestException("Report not ready. Status: " + report.getStatus());
        }
        return storageService.downloadWatermarked(report.getWatermarkedStoragePath());
    }

    public long countByStatus(ReportStatus status) {
        return reportRepository.countByStatus(status);
    }

    private void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        try {
            byte[] header = new byte[4];
            file.getInputStream().read(header);
            for (int i = 0; i < PDF_MAGIC.length; i++) {
                if (header[i] != PDF_MAGIC[i]) {
                    throw new BadRequestException("Invalid PDF file (magic bytes mismatch)");
                }
            }
        } catch (IOException e) {
            throw new BadRequestException("Cannot read file content");
        }
    }

    private ReportResponse toResponse(Report r) {
        return ReportResponse.builder()
                .reportId(r.getId())
                .filename(r.getOriginalFilename())
                .fileSizeBytes(r.getFileSizeBytes())
                .pageCount(r.getPageCount())
                .status(r.getStatus().name())
                .benchmarkTag(r.getBenchmarkTag())
                .checksumSha256(r.getChecksumSha256())
                .createdAt(r.getCreatedAt())
                .watermarkedAt(r.getStatus() == ReportStatus.READY ? r.getUpdatedAt() : null)
                .errorReason(r.getErrorReason())
                .build();
    }
}
