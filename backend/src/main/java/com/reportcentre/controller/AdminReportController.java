package com.reportcentre.controller;

import com.reportcentre.dto.ApiResponse;
import com.reportcentre.dto.ReportResponse;
import com.reportcentre.entity.enums.UploaderType;
import com.reportcentre.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<ReportResponse>>> listReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String benchmarkTag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ReportResponse> reports = reportService.getReports(status, benchmarkTag,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.success(reports));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ReportResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "benchmark_tag", required = false) String benchmarkTag,
            @RequestParam(value = "metadata", required = false) String metadata,
            Authentication auth) {
        ReportResponse response = reportService.upload(file, benchmarkTag, metadata,
                UploaderType.ADMIN_USER, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('OPERATOR', 'SUPER_ADMIN')")
    public ResponseEntity<InputStreamResource> download(@PathVariable String id) {
        var stream = reportService.downloadReport(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(stream));
    }
}
