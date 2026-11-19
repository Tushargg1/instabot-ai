package com.example.instagramreelai.controller;

import com.example.instagramreelai.config.AppProperties;
import com.example.instagramreelai.dto.MarketingLeadRequest;
import com.example.instagramreelai.dto.MarketingPlaybookRequest;
import com.example.instagramreelai.dto.MarketingStatsDto;
import com.example.instagramreelai.entity.InstagramAccount;
import com.example.instagramreelai.entity.MarketingLead;
import com.example.instagramreelai.entity.MarketingPlaybook;
import com.example.instagramreelai.repository.InstagramAccountRepository;
import com.example.instagramreelai.service.EncryptionService;
import com.example.instagramreelai.service.MarketingOutreachService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/marketing")
@RequiredArgsConstructor
public class MarketingController {

    private final MarketingOutreachService outreachService;
    private final InstagramAccountRepository accountRepository;
    private final EncryptionService encryptionService;
    private final AppProperties appProperties;

    @GetMapping("/leads")
    public ResponseEntity<Page<MarketingLead>> getLeads(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(
                Math.max(0, page),
                Math.max(1, Math.min(100, size)),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return ResponseEntity.ok(outreachService.getLeads(query, status, tag, pageRequest));
    }

    @GetMapping("/leads/{id}")
    public ResponseEntity<MarketingLead> getLead(@PathVariable UUID id) {
        return outreachService.getLead(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/leads")
    public ResponseEntity<MarketingLead> createLead(@RequestBody MarketingLeadRequest req) {
        return ResponseEntity.ok(outreachService.createLead(req));
    }

    @PutMapping("/leads/{id}")
    public ResponseEntity<MarketingLead> updateLead(@PathVariable UUID id, @RequestBody MarketingLeadRequest req) {
        return ResponseEntity.ok(outreachService.updateLead(id, req));
    }

    @DeleteMapping("/leads/{id}")
    public ResponseEntity<Void> deleteLead(@PathVariable UUID id) {
        outreachService.deleteLead(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<MarketingStatsDto> getStats() {
        return ResponseEntity.ok(outreachService.getStats());
    }

    @GetMapping("/tags")
    public ResponseEntity<List<String>> getTags() {
        return ResponseEntity.ok(outreachService.getTags());
    }

    @PostMapping("/leads/generate-first-message")
    public ResponseEntity<Map<String, String>> generateFirstMessage(@RequestBody MarketingLeadRequest req) {
        String msg = outreachService.generateFirstMessage(
                req.getContext(),
                req.getTag(),
                req.getProductOffer(),
                req.getSalesGoal(),
                req.getContactName()
        );
        return ResponseEntity.ok(Map.of("message", msg));
    }

    @PostMapping("/leads/{id}/send-outreach")
    public ResponseEntity<MarketingLead> sendOutreach(@PathVariable UUID id) {
        // Resolve active account & token
        String accountId = "17841431674180722";
        String token = appProperties.getInstagram().getAccessToken();
        var accOpt = accountRepository.findByInstagramAccountIdAndActiveTrue(accountId);
        if (accOpt.isPresent() && (token == null || token.isBlank())) {
            InstagramAccount acc = accOpt.get();
            if (acc.getAccessTokenEncrypted() != null) {
                try {
                    token = encryptionService.decrypt(acc.getAccessTokenEncrypted());
                } catch (Exception ignored) {}
            }
        }

        MarketingLead updated = outreachService.markOrSendOutreach(id, accountId, token);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/leads/{id}/simulate")
    public ResponseEntity<Map<String, String>> simulateResponse(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body
    ) {
        String input = body.getOrDefault("message", "");
        String reply = outreachService.simulateSalesResponse(id, input);
        return ResponseEntity.ok(Map.of("reply", reply));
    }

    // ── Playbooks & Sales Scripts Endpoints ──

    @GetMapping("/playbooks")
    public ResponseEntity<List<MarketingPlaybook>> getPlaybooks() {
        return ResponseEntity.ok(outreachService.getPlaybooks());
    }

    @GetMapping("/playbooks/{id}")
    public ResponseEntity<MarketingPlaybook> getPlaybook(@PathVariable UUID id) {
        return outreachService.getPlaybook(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/playbooks")
    public ResponseEntity<MarketingPlaybook> createPlaybook(@RequestBody MarketingPlaybookRequest req) {
        return ResponseEntity.ok(outreachService.createPlaybook(req));
    }

    @PutMapping("/playbooks/{id}")
    public ResponseEntity<MarketingPlaybook> updatePlaybook(@PathVariable UUID id, @RequestBody MarketingPlaybookRequest req) {
        return ResponseEntity.ok(outreachService.updatePlaybook(id, req));
    }

    @DeleteMapping("/playbooks/{id}")
    public ResponseEntity<Void> deletePlaybook(@PathVariable UUID id) {
        outreachService.deletePlaybook(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/playbooks/upload-pdf")
    public ResponseEntity<Map<String, String>> uploadPdf(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }
        try {
            String extracted = outreachService.extractTextFromPdf(file.getBytes());
            return ResponseEntity.ok(Map.of(
                    "filename", file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf",
                    "extractedText", extracted
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to parse PDF: " + e.getMessage()));
        }
    }
}
