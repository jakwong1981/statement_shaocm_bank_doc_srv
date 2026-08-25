package com.reportcentre.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
public class ClientResponse {
    private String id;
    private String clientName;
    private String apiKey;
    private String status;
    private String allowedIps;
    private Instant createdAt;
}
