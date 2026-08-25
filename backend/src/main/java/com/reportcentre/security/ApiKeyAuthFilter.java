package com.reportcentre.security;

import com.reportcentre.entity.ThirdPartyClient;
import com.reportcentre.entity.enums.ClientStatus;
import com.reportcentre.repository.ThirdPartyClientRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final ThirdPartyClientRepository clientRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/external/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader("X-API-KEY");
        if (apiKey == null || apiKey.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"status\":\"ERROR\",\"message\":\"Missing X-API-KEY header\"}");
            return;
        }

        var client = clientRepository.findByApiKey(apiKey);
        if (client.isEmpty() || client.get().getStatus() != ClientStatus.ACTIVE) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"status\":\"ERROR\",\"message\":\"Invalid or inactive API key\"}");
            return;
        }

        ThirdPartyClient c = client.get();
        request.setAttribute("clientId", c.getId());
        request.setAttribute("clientName", c.getClientName());

        var auth = new UsernamePasswordAuthenticationToken(
                c.getId(), null,
                List.of(new SimpleGrantedAuthority("ROLE_THIRD_PARTY")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        filterChain.doFilter(request, response);
    }
}
