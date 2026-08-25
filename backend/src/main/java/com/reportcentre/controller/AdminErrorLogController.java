package com.reportcentre.controller;

import com.reportcentre.dto.ApiResponse;
import com.reportcentre.dto.SystemErrorLogResponse;
import com.reportcentre.service.SystemErrorLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/admin/error-logs")
@RequiredArgsConstructor
public class AdminErrorLogController {

    private final SystemErrorLogService errorLogService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<SystemErrorLogResponse>>> getErrorLogs(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SystemErrorLogResponse> logs = errorLogService.getErrorLogs(
                from, to, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}
