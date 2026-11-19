package com.example.instagramreelai.controller;

import com.example.instagramreelai.dto.DmReplyRuleDto;
import com.example.instagramreelai.dto.DmReplyRuleRequest;
import com.example.instagramreelai.entity.AppUser;
import com.example.instagramreelai.repository.AppUserRepository;
import com.example.instagramreelai.service.DmAutoReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/dm-rules")
@RequiredArgsConstructor
public class DmReplyRuleController {

    private final DmAutoReplyService dmAutoReplyService;
    private final AppUserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<DmReplyRuleDto>> list(Authentication auth) {
        return ResponseEntity.ok(dmAutoReplyService.getRules(user(auth).getId()));
    }

    @PostMapping
    public ResponseEntity<DmReplyRuleDto> create(
            @RequestBody DmReplyRuleRequest req, Authentication auth) {
        return ResponseEntity.ok(dmAutoReplyService.createRule(user(auth).getId(), req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DmReplyRuleDto> update(
            @PathVariable UUID id, @RequestBody DmReplyRuleRequest req, Authentication auth) {
        return ResponseEntity.ok(dmAutoReplyService.updateRule(user(auth).getId(), id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication auth) {
        dmAutoReplyService.deleteRule(user(auth).getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<DmReplyRuleDto> toggle(
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body,
            Authentication auth) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        return ResponseEntity.ok(dmAutoReplyService.toggleRule(user(auth).getId(), id, enabled));
    }

    private AppUser user(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }
}
