package com.example.instagramreelai.controller;

import com.example.instagramreelai.config.AppProperties;
import com.example.instagramreelai.service.WebhookService;
import com.example.instagramreelai.service.WebhookSignatureService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/webhooks/instagram")
@RequiredArgsConstructor
public class WebhookController {

    private final AppProperties appProperties;
    private final WebhookService webhookService;
    private final WebhookSignatureService signatureService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {
        String configuredToken = appProperties.getInstagram().getWebhookVerifyToken();
        if ("subscribe".equals(mode) && configuredToken != null && configuredToken.equals(verifyToken)) {
            log.info("Webhook verification successful");
            return ResponseEntity.ok(challenge);
        }
        log.warn("Webhook verification failed");
        return ResponseEntity.status(403).body("Verification failed");
    }

    @PostMapping
    public ResponseEntity<String> receiveWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        if (!signatureService.isValid(rawPayload, signature)) {
            log.warn("Rejected Instagram webhook with invalid signature");
            return ResponseEntity.status(401).body("INVALID_SIGNATURE");
        }
        try {
            JsonNode payload = objectMapper.readTree(rawPayload);
            webhookService.processWebhookEvent(payload);
            return ResponseEntity.ok("EVENT_RECEIVED");
        } catch (IllegalArgumentException malformed) {
            log.warn("Rejected malformed Instagram webhook: {}", malformed.getMessage());
            return ResponseEntity.badRequest().body("INVALID_PAYLOAD");
        } catch (Exception persistenceFailure) {
            log.error("Durable webhook intake failed; requesting a Meta retry", persistenceFailure);
            return ResponseEntity.internalServerError().body("RETRY_REQUIRED");
        }
    }
}
