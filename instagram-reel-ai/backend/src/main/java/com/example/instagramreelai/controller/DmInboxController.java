package com.example.instagramreelai.controller;

import com.example.instagramreelai.dto.DmEventDto;
import com.example.instagramreelai.service.AutoReplyPauseService;
import com.example.instagramreelai.service.DmInboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/dm-inbox")
@RequiredArgsConstructor
public class DmInboxController {

    private final DmInboxService dmInboxService;
    private final AutoReplyPauseService autoReplyPauseService;

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

    @PostMapping("/send")
    public ResponseEntity<?> sendManualMessage(@RequestBody java.util.Map<String, String> body) {
        String recipientId = body.get("recipientId");
        String message = body.get("message");
        if (recipientId == null || message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "recipientId and message are required"));
        }
        boolean sent = dmInboxService.sendManualReply(recipientId, message);
        if (sent) {
            return ResponseEntity.ok(java.util.Map.of("status", "SENT"));
        } else {
            return ResponseEntity.status(500).body(java.util.Map.of("error", "Failed to send message via Instagram API"));
        }
    }

    @GetMapping("/paused-users")
    public ResponseEntity<Set<String>> getPausedUsers() {
        return ResponseEntity.ok(autoReplyPauseService.getPausedUserIdentifiers());
    }

    @PostMapping("/pause-user")
    public ResponseEntity<?> pauseUser(@RequestBody Map<String, String> body) {
        String identifier = body.get("userIdentifier");
        String username = body.get("username");
        String senderId = body.get("senderId");
        String reason = body.getOrDefault("reason", "Manual pause from DM Inbox");

        if (identifier == null || identifier.isBlank()) {
            identifier = senderId != null ? senderId : username;
        }

        if (identifier == null || identifier.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "userIdentifier, senderId, or username is required"));
        }

        autoReplyPauseService.pauseUser(identifier, username, senderId, reason);
        return ResponseEntity.ok(Map.of("status", "PAUSED", "userIdentifier", identifier));
    }

    @PostMapping("/resume-user")
    public ResponseEntity<?> resumeUser(@RequestBody Map<String, String> body) {
        String identifier = body.get("userIdentifier");
        if (identifier == null || identifier.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "userIdentifier is required"));
        }

        autoReplyPauseService.resumeUser(identifier);
        return ResponseEntity.ok(Map.of("status", "ACTIVE", "userIdentifier", identifier));
    }
}
