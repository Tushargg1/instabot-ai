package com.example.instagramreelai.controller;

import com.example.instagramreelai.dto.PublicSubmissionTokenDtos.*;
import com.example.instagramreelai.entity.AppUser;
import com.example.instagramreelai.entity.Reel;
import com.example.instagramreelai.entity.ReelRequest;
import com.example.instagramreelai.job.JobService;
import com.example.instagramreelai.repository.AppUserRepository;
import com.example.instagramreelai.repository.ReelRepository;
import com.example.instagramreelai.repository.ReelRequestRepository;
import com.example.instagramreelai.service.PublicSubmissionTokenService;
import com.example.instagramreelai.service.ReelCanonicalizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PublicSubmissionTokenController {
    private final PublicSubmissionTokenService tokenService;
    private final AppUserRepository userRepository;
    private final ReelCanonicalizationService canonicalizationService;
    private final ReelRepository reelRepository;
    private final ReelRequestRepository requestRepository;
    private final JobService jobService;

    @PostMapping("/api/public-submission-tokens")
    public ResponseEntity<CreateResponse> create(
            @Valid @RequestBody(required = false) CreateRequest request,
            Authentication authentication) {
        AppUser owner = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
        Long ttl = request == null ? null : request.expiresInSeconds();
        String label = request == null ? null : request.publicLabel();
        return ResponseEntity.status(HttpStatus.CREATED).body(tokenService.create(owner, ttl, label));
    }

    @GetMapping("/api/public/submissions/{token}")
    public ResponseEntity<MetadataResponse> metadata(@PathVariable String token) {
        return ResponseEntity.ok(tokenService.metadata(token));
    }

    @PostMapping("/api/public/submissions/{token}/consume")
    public ResponseEntity<ConsumeResponse> consume(@PathVariable String token) {
        var validated = tokenService.consumeForIntegration(token);
        return ResponseEntity.ok(new ConsumeResponse(
                validated.validationId(), "VALIDATED", validated.integrationHook(),
                validated.consumedAt()));
    }

    @PostMapping("/api/public/submissions/{token}/submit")
    public ResponseEntity<?> submitReel(
            @PathVariable String token,
            @RequestBody Map<String, String> body) {
        String reelUrl = body.get("reelUrl");
        if (reelUrl == null || reelUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "reelUrl is required"));
        }
        var validated = tokenService.consumeForIntegration(token);

        // Canonicalize
        var canonical = canonicalizationService.canonicalize(null, reelUrl)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Could not parse the provided URL as a valid Reel"));

        // Find or create canonical reel
        AppUser owner = userRepository.findById(validated.ownerUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Owner not found"));
        Reel reel = reelRepository.findByUserIdAndCanonicalKey(owner.getId(), canonical.key())
                .orElseGet(() -> reelRepository.saveAndFlush(Reel.builder()
                        .canonicalKey(canonical.key())
                        .user(owner)
                        .sourceUrl(canonical.normalizedUrl())
                        .processingStatus("RECEIVED")
                        .requestCount(0L)
                        .build()));

        // Create a reel request (no webhook event, source = WEB_SUBMISSION)
        ReelRequest request = ReelRequest.builder()
                .reel(reel)
                .source("WEB_SUBMISSION")
                .externalRequestId(validated.validationId().toString())
                .status("PENDING")
                .replyStatus("NOT_APPLICABLE")
                .build();
        requestRepository.save(request);

        reel.setRequestCount((reel.getRequestCount() == null ? 0L : reel.getRequestCount()) + 1);
        reel.setLastRequestedAt(Instant.now());
        reelRepository.save(reel);

        // Create job
        String jobType = "COMPLETED".equals(reel.getProcessingStatus()) ? "RESOURCE_DETECTION" : "AI_PIPELINE";
        jobService.createJobIfAbsent(reel, jobType);

        return ResponseEntity.ok(Map.of(
                "status", "SUBMITTED",
                "reelId", reel.getId().toString(),
                "canonicalKey", reel.getCanonicalKey()));
    }
}
