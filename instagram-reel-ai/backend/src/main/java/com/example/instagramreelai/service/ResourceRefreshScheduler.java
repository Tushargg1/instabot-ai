package com.example.instagramreelai.service;

import com.example.instagramreelai.job.JobService;
import com.example.instagramreelai.job.JobType;
import com.example.instagramreelai.repository.ReelResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceRefreshScheduler {
    private final ReelResourceRepository resourceRepository;
    private final JobService jobService;

    @Scheduled(fixedDelayString = "${app.resources.refresh-interval-ms:60000}")
    public void enqueueExpiredResources() {
        resourceRepository.findByExpiresAtBeforeOrderByExpiresAtAsc(
                        Instant.now(), PageRequest.of(0, 100))
                .forEach(resource -> {
                    try {
                        jobService.createJobIfAbsent(resource.getReel(), JobType.RESOURCE_REFRESH);
                    } catch (RuntimeException exception) {
                        log.warn("Could not enqueue refresh for resource {}: {}",
                                resource.getId(), exception.getMessage());
                    }
                });
    }
}
