package com.example.instagramreelai.service;

import com.example.instagramreelai.config.AppProperties;
import com.example.instagramreelai.entity.InstagramAccount;
import com.example.instagramreelai.entity.Reel;
import com.example.instagramreelai.instagram.InstagramClient;
import com.example.instagramreelai.repository.InstagramAccountRepository;
import com.example.instagramreelai.repository.ReelRepository;
import com.example.instagramreelai.repository.WebhookEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookEventRepository eventRepository;
    private final InstagramAccountRepository accountRepository;
    private final ReelRepository reelRepository;
    private final ReelCanonicalizationService canonicalizationService;
    private final CanonicalReelStore canonicalReelStore;
    private final ReelIngestionService ingestionService;
    private final ObjectMapper objectMapper;
    private final DmAutoReplyService dmAutoReplyService;
    private final AppProperties appProperties;
    private final InstagramClient instagramClient;

    /**
     * Performs only durable, idempotent intake. Expensive media and AI work stays queued.
     */
    public void processWebhookEvent(JsonNode payload) {
        JsonNode entries = payload.path("entry");
        if (!entries.isArray()) {
            log.warn("No entry array in webhook payload");
            return;
        }
        for (JsonNode entry : entries) {
            JsonNode messaging = entry.path("messaging");
            if (!messaging.isArray()) continue;
            for (JsonNode message : messaging) processMessage(entry, message, payload);
        }
    }

    private void processMessage(JsonNode entry, JsonNode messageNode, JsonNode fullPayload) {
        String messageId = messageNode.path("message").path("mid").asText();
        if (messageId.isBlank() || eventRepository.existsByExternalEventId(messageId)) return;

        String accountId = entry.path("id").asText();
        InstagramAccount account = accountRepository.findByInstagramAccountIdAndActiveTrue(accountId)
                .orElse(null);
        if (account == null) {
            // In development: accept the event anyway using the first active account
            account = accountRepository.findAll().stream()
                    .filter(a -> Boolean.TRUE.equals(a.getActive()))
                    .findFirst().orElse(null);
            if (account == null) {
                log.warn("Ignoring webhook for unknown Instagram account {} and no fallback available", accountId);
                return;
            }
            log.info("Webhook from unregistered account {}; using fallback account {}", accountId, account.getUsername());
        }

        String senderId = messageNode.path("sender").path("id").asText();
        String recipientId = messageNode.path("recipient").path("id").asText();
        String dmText = messageNode.path("message").path("text").asText(null);

        // Detect if this message is a Reel attachment or contains a Reel URL
        JsonNode rawAttachments = messageNode.path("message").path("attachments");
        boolean hasReelAttachment = false;
        String reelMediaUrl = null;
        String reelMediaId = null;

        if (rawAttachments.isArray() && !rawAttachments.isEmpty()) {
            JsonNode firstAtt = rawAttachments.get(0);
            String attType = firstAtt.path("type").asText("");
            if ("video".equals(attType) || "audio".equals(attType) || "share".equals(attType)
                    || "ig_reel".equals(attType) || "reel".equals(attType)) {
                hasReelAttachment = true;
                JsonNode payload = firstAtt.path("payload");
                reelMediaUrl = payload.path("url").asText(null);
                reelMediaId = payload.has("reel_video_id")
                        ? payload.path("reel_video_id").asText(null)
                        : payload.path("id").asText(null);
            }
        }

        String detectedPlatform = "INSTAGRAM";
        // Check if message text contains a video link (Instagram, YouTube Shorts, or TikTok)
        if (!hasReelAttachment && dmText != null) {
            String lower = dmText.toLowerCase().trim();
            if (lower.contains("youtube.com/shorts/") || lower.contains("youtu.be/")) {
                hasReelAttachment = true;
                reelMediaUrl = dmText.trim();
                detectedPlatform = "YOUTUBE";
            } else if (lower.contains("tiktok.com/") || lower.contains("vm.tiktok.com/")) {
                hasReelAttachment = true;
                reelMediaUrl = dmText.trim();
                detectedPlatform = "TIKTOK";
            } else if (lower.contains("instagram.com/reel/") || lower.contains("instagram.com/reels/") || lower.contains("instagram.com/p/")) {
                hasReelAttachment = true;
                reelMediaUrl = dmText.trim();
                detectedPlatform = "INSTAGRAM";
            }
        }

        final boolean isReel = hasReelAttachment;
        final String videoPlatform = detectedPlatform;
        final InstagramAccount finalAccount = account;
        final String finalMediaUrl = reelMediaUrl;
        final String finalMediaId = reelMediaId;

        // ── Fast-path auto-reply thread ──
        Thread t = new Thread(() -> {
            try {
                if (isReel) {
                    // Send instant acknowledgment for shared Reels / Shorts / TikToks
                    String token = appProperties.getInstagram().getAccessToken();
                    if (token != null && !token.isBlank()) {
                        String platformName = "YOUTUBE".equals(videoPlatform) ? "YouTube Short" :
                                ("TIKTOK".equals(videoPlatform) ? "TikTok" : "Reel");
                        instagramClient.sendReply(finalAccount.getInstagramAccountId(), senderId,
                                "⏳ Got your " + platformName + "! Extracting the main points and key data for you, one sec...", token);
                    }
                } else {
                    dmAutoReplyService.handleIncomingDm(senderId, dmText, finalAccount.getInstagramAccountId());
                }
            } catch (Exception ex) {
                log.warn("DM auto-reply error for mid={}: {}", messageId, ex.getMessage());
            }
        });
        t.setName("dm-autoreply-" + messageId);
        t.setDaemon(true);
        t.start();
        // ─────────────────────────────────

        // Always persist the raw event so we can inspect all incoming DMs (including creator replies)
        try {
            var event = new com.example.instagramreelai.entity.InstagramWebhookEvent();
            event.setExternalEventId(messageId);
            event.setSenderId(senderId);
            event.setRecipientId(recipientId);
            event.setRawPayload(objectMapper.writeValueAsString(fullPayload));
            event.setReceivedAt(Instant.now());
            event.setProcessed(false);

            if (rawAttachments.isArray() && !rawAttachments.isEmpty()) {
                event.setEventType(rawAttachments.get(0).path("type").asText());
            } else if (isReel) {
                event.setEventType("ig_reel");
            } else {
                event.setEventType("text");
            }
            eventRepository.save(event);
            log.info("Persisted webhook event mid={} type={} sender={}", messageId, event.getEventType(), senderId);
        } catch (Exception e) {
            log.warn("Failed to persist webhook event {}: {}", messageId, e.getMessage());
        }

        // Now process reel attachments for the AI pipeline
        if (!isReel) return;

        ReelCanonicalizationService.CanonicalReel canonical = canonicalizationService
                .canonicalize(finalMediaId, finalMediaUrl)
                .orElse(new ReelCanonicalizationService.CanonicalReel("reel:" + messageId, finalMediaUrl));

        Reel reel;
        try {
            reel = canonicalReelStore.resolveOrCreate(canonical, finalMediaId, account);
        } catch (DataIntegrityViolationException race) {
            reel = reelRepository.findByCanonicalKey(canonical.key()).orElseThrow(() -> race);
        }

        if (videoPlatform != null && !videoPlatform.equalsIgnoreCase(reel.getPlatform())) {
            reel.setPlatform(videoPlatform);
            reelRepository.save(reel);
        }

        try {
            ReelIngestionService.IngestionResult result = ingestionService.recordRequest(
                    messageId, senderId, recipientId,
                    objectMapper.writeValueAsString(fullPayload), reel);
            log.info("Ingested reel request event={} canonicalReel={} duplicate={} jobCreated={}",
                    messageId, reel.getCanonicalKey(), result.duplicateEvent(), result.jobCreated());
        } catch (DataIntegrityViolationException race) {
            // A concurrent request won a single-flight constraint. Meta should retry this event.
            throw race;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not persist Reel request " + messageId, exception);
        }
    }
}
