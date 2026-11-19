package com.example.instagramreelai.controller;

import com.example.instagramreelai.entity.CapturedLead;
import com.example.instagramreelai.service.LeadCaptureService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadCaptureService leadCaptureService;

    @GetMapping
    public ResponseEntity<Page<CapturedLead>> getLeads(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(0, page),
                Math.max(1, Math.min(100, size)),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return ResponseEntity.ok(leadCaptureService.getLeads(query, pageRequest));
    }
}
