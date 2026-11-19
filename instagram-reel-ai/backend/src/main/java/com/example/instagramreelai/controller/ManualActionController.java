package com.example.instagramreelai.controller;

import com.example.instagramreelai.dto.ManualActionClaimRequest;
import com.example.instagramreelai.dto.ManualActionCompleteRequest;
import com.example.instagramreelai.dto.ManualActionDto;
import com.example.instagramreelai.dto.ManualActionFailRequest;
import com.example.instagramreelai.service.ManualActionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/manual-actions")
@RequiredArgsConstructor
@Validated
public class ManualActionController {

    private final ManualActionService manualActionService;

    @GetMapping
    public ResponseEntity<Page<ManualActionDto>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(manualActionService.list(PageRequest.of(page, size), status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ManualActionDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(manualActionService.get(id));
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<ManualActionDto> claim(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ManualActionClaimRequest request,
            Authentication authentication) {
        Integer leaseSeconds = request == null ? null : request.leaseSeconds();
        return ResponseEntity.ok(manualActionService.claim(id, authentication.getName(), leaseSeconds));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ManualActionDto> complete(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) ManualActionCompleteRequest request,
            Authentication authentication) {
        String result = request == null ? null : request.result();
        return ResponseEntity.ok(manualActionService.complete(id, authentication.getName(), result));
    }

    @PostMapping("/{id}/fail")
    public ResponseEntity<ManualActionDto> fail(
            @PathVariable UUID id,
            @Valid @RequestBody ManualActionFailRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(manualActionService.fail(
                id, authentication.getName(), request.reason()));
    }
}
