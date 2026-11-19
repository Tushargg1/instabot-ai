package com.example.instagramreelai.controller;

import com.example.instagramreelai.dto.WorkerInstanceResponse;
import com.example.instagramreelai.service.WorkerRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workers")
@RequiredArgsConstructor
public class WorkerInstanceController {
    private final WorkerRegistryService workerRegistryService;

    @GetMapping("/active")
    public ResponseEntity<List<WorkerInstanceResponse>> active() {
        return ResponseEntity.ok(workerRegistryService.listActive());
    }

    @GetMapping("/stale")
    public ResponseEntity<List<WorkerInstanceResponse>> stale() {
        return ResponseEntity.ok(workerRegistryService.listStale());
    }
}