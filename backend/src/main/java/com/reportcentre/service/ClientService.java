package com.reportcentre.service;

import com.reportcentre.dto.ClientCreateRequest;
import com.reportcentre.dto.ClientResponse;
import com.reportcentre.entity.ThirdPartyClient;
import com.reportcentre.entity.enums.ClientStatus;
import com.reportcentre.exception.BadRequestException;
import com.reportcentre.exception.ResourceNotFoundException;
import com.reportcentre.repository.ThirdPartyClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ThirdPartyClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public List<ClientResponse> getAllClients() {
        return clientRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public ClientCreateResult createClient(ClientCreateRequest request) {
        String apiKey = generateApiKey();
        String apiSecret = generateApiSecret();

        ThirdPartyClient client = ThirdPartyClient.builder()
                .id(UUID.randomUUID().toString())
                .clientName(request.getClientName())
                .apiKey(apiKey)
                .apiSecretHash(passwordEncoder.encode(apiSecret))
                .status(ClientStatus.ACTIVE)
                .allowedIps(request.getAllowedIps() != null ?
                        String.join(",", request.getAllowedIps()) : "")
                .build();

        clientRepository.save(client);
        return new ClientCreateResult(toResponse(client), apiSecret);
    }

    public ClientResponse updateClientStatus(String id, ClientStatus status) {
        ThirdPartyClient client = clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + id));
        client.setStatus(status);
        clientRepository.save(client);
        return toResponse(client);
    }

    private ClientResponse toResponse(ThirdPartyClient c) {
        return ClientResponse.builder()
                .id(c.getId())
                .clientName(c.getClientName())
                .apiKey(c.getApiKey())
                .status(c.getStatus().name())
                .allowedIps(c.getAllowedIps())
                .createdAt(c.getCreatedAt())
                .build();
    }

    private String generateApiKey() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return "ak_live_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateApiSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record ClientCreateResult(ClientResponse client, String rawSecret) {}
}
