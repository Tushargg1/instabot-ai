package com.example.instagramreelai.controller;

import com.example.instagramreelai.config.AppProperties;
import com.example.instagramreelai.dto.AuthResponse;
import com.example.instagramreelai.entity.AppUser;
import com.example.instagramreelai.repository.AppUserRepository;
import com.example.instagramreelai.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Profile("dev")
public class DemoAuthController {

    private final AppProperties appProperties;
    private final AppUserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/demo-login")
    public ResponseEntity<AuthResponse> demoLogin() {
        AppUser admin = userRepository.findByUsername(appProperties.getAdmin().getUsername())
                .orElseThrow(() -> new IllegalStateException("Demo admin is not available"));

        String token = jwtTokenProvider.generateToken(admin.getUsername(), admin.getRole());
        return ResponseEntity.ok(new AuthResponse(token, admin.getUsername(), admin.getRole()));
    }
}
