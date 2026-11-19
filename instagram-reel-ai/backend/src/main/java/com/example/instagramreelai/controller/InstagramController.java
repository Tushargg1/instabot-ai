package com.example.instagramreelai.controller;

import com.example.instagramreelai.config.AppProperties;
import com.example.instagramreelai.entity.AppUser;
import com.example.instagramreelai.entity.InstagramAccount;
import com.example.instagramreelai.repository.AppUserRepository;
import com.example.instagramreelai.repository.InstagramAccountRepository;
import com.example.instagramreelai.repository.ProcessingJobRepository;
import com.example.instagramreelai.repository.ReelRepository;
import com.example.instagramreelai.repository.ReelRequestRepository;
import com.example.instagramreelai.service.InstagramOAuthService;
import com.example.instagramreelai.service.WebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/instagram")
@RequiredArgsConstructor
public class InstagramController {

    private final AppProperties appProperties;
    private final InstagramOAuthService oauthService;
    private final AppUserRepository userRepository;
    private final InstagramAccountRepository accountRepository;
    private final ReelRepository reelRepository;
    private final ReelRequestRepository reelRequestRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus(Authentication authentication) {
        AppUser user = getUser(authentication);
        InstagramAccount account = oauthService.getConnection(user.getId()).orElse(null);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("available", oauthService.isConfigured());
        status.put("connected", account != null);
        status.put("configured", account != null);
        status.put("accountId", account != null ? account.getInstagramAccountId() : "");
        status.put("username", account != null && account.getUsername() != null ? account.getUsername() : "");
        status.put("displayName", account != null && account.getDisplayName() != null ? account.getDisplayName() : "");
        status.put("profilePictureUrl", account != null && account.getProfilePictureUrl() != null ? account.getProfilePictureUrl() : "");
        status.put("tokenExpiresAt", account != null ? account.getTokenExpiresAt() : null);
        status.put("scopes", account != null && account.getScopes() != null
                ? Arrays.stream(account.getScopes().split(",")).map(String::trim).toList() : java.util.List.of());
        status.put("webhookConfigured", hasText(appProperties.getInstagram().getWebhookVerifyToken()));
        status.put("testMode", environment.acceptsProfiles(Profiles.of("dev")));
        return ResponseEntity.ok(status);
    }

    @PostMapping("/oauth/start")
    public ResponseEntity<Map<String, String>> startOAuth(Authentication authentication) {
        AppUser user = getUser(authentication);
        return ResponseEntity.ok(Map.of("authorizationUrl", oauthService.createAuthorizationUrl(user)));
    }

    @GetMapping("/oauth/callback")
    public ResponseEntity<Void> oauthCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription) {
        if (hasText(error)) {
            return frontendRedirect("error", hasText(errorDescription) ? errorDescription : error);
        }
        try {
            oauthService.completeAuthorization(code, state);
            return frontendRedirect("success", null);
        } catch (Exception exception) {
            log.warn("Instagram OAuth callback failed: {}", exception.getMessage());
            return frontendRedirect("error", exception.getMessage());
        }
    }

    @DeleteMapping("/connection")
    public ResponseEntity<Void> disconnect(Authentication authentication) {
        oauthService.disconnect(getUser(authentication).getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testWebhook(Authentication authentication) {
        if (!environment.acceptsProfiles(Profiles.of("dev"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        try {
            AppUser user = getUser(authentication);
            String accountId = "test-" + user.getId();
            InstagramAccount account = accountRepository.findByInstagramAccountId(accountId)
                    .orElseGet(InstagramAccount::new);
            account.setInstagramAccountId(accountId);
            account.setUser(user);
            account.setUsername("demo_instagram");
            account.setDisplayName("Demo Instagram");
            account.setConnectionStatus("MOCK");
            account.setActive(true);
            accountRepository.save(account);

            long now = System.currentTimeMillis();
            String testPayload = """
                {"object":"instagram","entry":[{"id":"%s","time":%d,"messaging":[{
                "sender":{"id":"test-sender-123"},"recipient":{"id":"%s"},"timestamp":%d,
                "message":{"mid":"test-mid-%d","attachments":[{"type":"video","payload":{
                "id":"demo-reel-shared","url":"https://mock-cdn.example.com/test-reel.mp4"}}]}}]}]}
                """.formatted(accountId, now, accountId, now, now);
            JsonNode payload = objectMapper.readTree(testPayload);
            webhookService.processWebhookEvent(payload);
            var demoReel = reelRepository.findByCanonicalKey("instagram:media:demo-reel-shared")
                    .orElseThrow(() -> new IllegalStateException("Canonical demo Reel was not created"));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "test_event_sent");
            result.put("canonicalReelId", demoReel.getId());
            result.put("requestCount", reelRequestRepository.countByReelId(demoReel.getId()));
            result.put("aiPipelineJobs", processingJobRepository.countByReelIdAndJobType(
                    demoReel.getId(), "AI_PIPELINE"));
            result.put("replyJobs", processingJobRepository.countByReelIdAndJobType(
                    demoReel.getId(), "REPLY"));
            return ResponseEntity.ok(result);
        } catch (Exception exception) {
            return ResponseEntity.internalServerError().body(Map.of("error", exception.getMessage()));
        }
    }

    private ResponseEntity<Void> frontendRedirect(String outcome, String message) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(appProperties.getInstagram().getFrontendRedirectUri())
                .queryParam("oauth", outcome);
        if (hasText(message)) builder.queryParam("message", message);
        URI location = builder.build().encode().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    private AppUser getUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
