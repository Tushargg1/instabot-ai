package com.example.instagramreelai.controller;

import com.example.instagramreelai.dto.ReelResourceDto;
import com.example.instagramreelai.repository.AppUserRepository;
import com.example.instagramreelai.service.ResourceDetectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ReelResourceController {
    private final ResourceDetectionService resourceService;
    private final AppUserRepository userRepository;

    @GetMapping
    public ResponseEntity<Page<ReelResourceDto>> list(@RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size,
                                                       Authentication authentication) {
        return ResponseEntity.ok(resourceService.list(owner(authentication),
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReelResourceDto> detail(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(resourceService.detail(id, owner(authentication)));
    }

    @PostMapping("/{id}/refresh")
    public ResponseEntity<ReelResourceDto> refresh(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.accepted().body(resourceService.requestRefresh(id, owner(authentication)));
    }

    private UUID owner(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow().getId();
    }
}
