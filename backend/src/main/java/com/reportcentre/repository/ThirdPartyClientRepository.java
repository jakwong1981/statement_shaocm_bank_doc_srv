package com.reportcentre.repository;

import com.reportcentre.entity.ThirdPartyClient;
import com.reportcentre.entity.enums.ClientStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ThirdPartyClientRepository extends JpaRepository<ThirdPartyClient, String> {
    Optional<ThirdPartyClient> findByApiKey(String apiKey);
    List<ThirdPartyClient> findByStatus(ClientStatus status);
    boolean existsByApiKey(String apiKey);
}
