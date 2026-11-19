package com.example.instagramreelai.controller;

import com.example.instagramreelai.entity.AppUser;
import com.example.instagramreelai.entity.ManualActionStatus;
import com.example.instagramreelai.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final ReelRepository reelRepository;
    private final ProcessingJobRepository jobRepository;
    private final ReelResourceRepository resourceRepository;
    private final ReelRequestRepository requestRepository;
    private final ManualActionRepository manualActionRepository;
    private final AppUserRepository userRepository;

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics(Authentication auth) {
        UUID userId = userRepository.findByUsername(auth.getName())
                .map(AppUser::getId).orElseThrow();

        long totalReels = reelRepository.countByUserId(userId);
        long totalJobs = jobRepository.countByReelUserId(userId);
        long pendingJobs = jobRepository.countByReelUserIdAndStatus(userId, "QUEUED");
        long failedJobs = jobRepository.countByReelUserIdAndStatus(userId, "FAILED");
        long totalResources = resourceRepository.countByReelUserId(userId);
        long availableResources = resourceRepository.countByReelUserIdAndStatus(userId, "AVAILABLE");
        long totalRequests = requestRepository.countByReelUserId(userId);
        long pendingActions = manualActionRepository.countByStatus(ManualActionStatus.PENDING);

        return ResponseEntity.ok(Map.of(
                "totalReels", totalReels,
                "totalJobs", totalJobs,
                "pendingJobs", pendingJobs,
                "failedJobs", failedJobs,
                "totalResources", totalResources,
                "availableResources", availableResources,
                "totalRequests", totalRequests,
                "pendingActions", pendingActions
        ));
    }
}
