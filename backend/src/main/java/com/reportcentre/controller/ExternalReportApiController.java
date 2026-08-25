package com.reportcentre.controller;

import com.reportcentre.dto.ApiResponse;
import com.reportcentre.dto.ReportResponse;
import com.reportcentre.entity.enums.UploaderType;
import com.reportcentre.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/external/reports")
@RequiredArgsConstructor
public class ExternalReportApiController {

    private final ReportService reportService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReportResponse>> ingest(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "report_id", required = false) String reportId,
            @RequestParam(value = "benchmark_tag", required = false) String benchmarkTag,
            @RequestParam(value = "metadata", required = false) String metadata,
            HttpServletRequest request) {
        String clientId = (String) request.getAttribute("clientId");
        ReportResponse response = reportService.uploadOrReplace(reportId, file, benchmarkTag, metadata,
                UploaderType.THIRD_PARTY, clientId);
        return ResponseEntity.status(202).body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReportResponse>> getStatus(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(reportService.getReport(id)));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<ApiResponse<Map<String, String>>> download(@PathVariable String id) {
        String downloadUrl = reportService.getDownloadUrl(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "report_id", id,
                "download_url", downloadUrl
        )));
    }
}
