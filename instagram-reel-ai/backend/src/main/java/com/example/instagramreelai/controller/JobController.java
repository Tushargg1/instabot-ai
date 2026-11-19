package com.example.instagramreelai.controller;

import com.example.instagramreelai.dto.ProcessingJobDto;
import com.example.instagramreelai.service.ProcessingJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final ProcessingJobService processingJobService;

    @GetMapping
    public ResponseEntity<Page<ProcessingJobDto>> listJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        Page<ProcessingJobDto> jobs = processingJobService.listJobs(PageRequest.of(page, size), status);
        return ResponseEntity.ok(jobs);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProcessingJobDto> getJob(@PathVariable UUID id) {
        return processingJobService.getJobById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ProcessingJobDto> retryJob(@PathVariable UUID id) {
        return processingJobService.retryJob(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
