package com.example.instagramreelai.controller;

import com.example.instagramreelai.dto.ReelDto;
import com.example.instagramreelai.dto.ReelListDto;
import com.example.instagramreelai.entity.AppUser;
import com.example.instagramreelai.repository.AppUserRepository;
import com.example.instagramreelai.service.ReelService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/reels")
@RequiredArgsConstructor
public class ReelController {

    private final ReelService reelService;
    private final AppUserRepository userRepository;

    @GetMapping
    public ResponseEntity<Page<ReelListDto>> listReels(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            Authentication auth) {
        UUID userId = getUserId(auth);
        Page<ReelListDto> reels = reelService.listReels(userId, PageRequest.of(page, size), status);
        return ResponseEntity.ok(reels);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReelDto> getReel(@PathVariable UUID id, Authentication auth) {
        UUID userId = getUserId(auth);
        return reelService.getReelById(id, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReel(@PathVariable UUID id, Authentication auth) {
        UUID userId = getUserId(auth);
        reelService.deleteReel(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<Page<ReelListDto>> searchReels(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        UUID userId = getUserId(auth);
        Page<ReelListDto> results = reelService.searchReels(userId, q, PageRequest.of(page, size));
        return ResponseEntity.ok(results);
    }

    private UUID getUserId(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .map(AppUser::getId)
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }
}
