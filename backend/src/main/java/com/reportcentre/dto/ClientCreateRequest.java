package com.reportcentre.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class ClientCreateRequest {
    @NotBlank
    private String clientName;
    private List<String> allowedIps;
}
