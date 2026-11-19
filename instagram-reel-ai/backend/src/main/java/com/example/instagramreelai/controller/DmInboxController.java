package com.example.instagramreelai.controller;

import com.example.instagramreelai.dto.DmEventDto;
import com.example.instagramreelai.service.DmInboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dm-inbox")
@RequiredArgsConstructor
public class DmInboxController {

    private final DmInboxService dmInboxService;

    /**
     * GET /api/dm-inbox?page=0&size=50&senderId=optional
     * Returns paginated DM events, newest first.
     */
    @GetMapping
    public ResponseEntity<Page<DmEventDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String senderId) {
        return ResponseEntity.ok(dmInboxService.listEvents(page, Math.min(size, 100), senderId));
    }
}
