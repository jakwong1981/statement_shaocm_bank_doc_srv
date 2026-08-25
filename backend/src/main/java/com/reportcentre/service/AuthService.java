package com.reportcentre.service;

import com.reportcentre.dto.LoginRequest;
import com.reportcentre.dto.LoginResponse;
import com.reportcentre.entity.User;
import com.reportcentre.exception.BadRequestException;
import com.reportcentre.exception.ResourceNotFoundException;
import com.reportcentre.repository.UserRepository;
import com.reportcentre.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Invalid credentials");
        }

        if (!user.getIsActive()) {
            throw new BadRequestException("Account is deactivated");
        }

        String accessToken = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        return new LoginResponse(accessToken, refreshToken, user.getUsername(), user.getRole().name());
    }

    public LoginResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BadRequestException("Invalid refresh token");
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String newAccessToken = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername());

        return new LoginResponse(newAccessToken, newRefreshToken, user.getUsername(), user.getRole().name());
    }
}
