package com.reportcentre.controller;

import com.reportcentre.dto.ApiResponse;
import com.reportcentre.dto.ClientCreateRequest;
import com.reportcentre.dto.ClientResponse;
import com.reportcentre.entity.enums.ClientStatus;
import com.reportcentre.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/clients")
@RequiredArgsConstructor
public class AdminClientController {

    private final ClientService clientService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<ClientResponse>>> listClients() {
        return ResponseEntity.ok(ApiResponse.success(clientService.getAllClients()));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createClient(
            @Valid @RequestBody ClientCreateRequest request) {
        var result = clientService.createClient(request);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "client", result.client(),
                "apiSecret", result.rawSecret(),
                "warning", "Store this secret securely - it will not be shown again"
        )));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ClientResponse>> updateStatus(
            @PathVariable String id,
            @RequestParam ClientStatus status) {
        return ResponseEntity.ok(ApiResponse.success(clientService.updateClientStatus(id, status)));
    }
}
