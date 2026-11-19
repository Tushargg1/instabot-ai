package com.example.instagramreelai.service;

import com.example.instagramreelai.entity.InstagramAccount;
import com.example.instagramreelai.entity.Reel;
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

        // ── Fast-path DM auto-reply: FIRE IMMEDIATELY in parallel with DB persistence ──
        final InstagramAccount finalAccount = account;
        Thread t = new Thread(() -> {
            try {
                dmAutoReplyService.handleIncomingDm(senderId, dmText, finalAccount.getInstagramAccountId());
            } catch (Exception ex) {
                log.warn("DM auto-reply error for mid={}: {}", messageId, ex.getMessage());
            }
        });
        t.setName("dm-autoreply-" + messageId);
        t.setDaemon(true);
        t.start();
        // ──────────────────────────────────────────────────────────────────────────────

        // Always persist the raw event so we can inspect all incoming DMs (including creator replies)
        try {
            var event = new com.example.instagramreelai.entity.InstagramWebhookEvent();
            event.setExternalEventId(messageId);
            event.setSenderId(senderId);
            event.setRecipientId(recipientId);
            event.setRawPayload(objectMapper.writeValueAsString(fullPayload));
            event.setReceivedAt(Instant.now());
            event.setProcessed(false);

            JsonNode rawAttachments = messageNode.path("message").path("attachments");
            if (rawAttachments.isArray() && !rawAttachments.isEmpty()) {
                event.setEventType(rawAttachments.get(0).path("type").asText());
            } else {
                event.setEventType("text");
            }
            eventRepository.save(event);
            log.info("Persisted webhook event mid={} type={} sender={}", messageId, event.getEventType(), senderId);
        } catch (Exception e) {
            log.warn("Failed to persist webhook event {}: {}", messageId, e.getMessage());
        }

        // Now process reel attachments for the AI pipeline
        JsonNode attachments = messageNode.path("message").path("attachments");
        if (!attachments.isArray() || attachments.isEmpty()) return;
        JsonNode attachment = attachments.get(0);
        String type = attachment.path("type").asText();
        if (!"video".equals(type) && !"audio".equals(type) && !"share".equals(type)
                && !"ig_reel".equals(type) && !"reel".equals(type)) return;

        JsonNode payload = attachment.path("payload");
        String mediaUrl = payload.path("url").asText(null);
        // ig_reel uses "reel_video_id" instead of "id"
        String mediaId = payload.has("reel_video_id")
                ? payload.path("reel_video_id").asText(null)
                : payload.path("id").asText(null);
        ReelCanonicalizationService.CanonicalReel canonical = canonicalizationService
                .canonicalize(mediaId, mediaUrl)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Webhook did not contain a stable media ID or usable URL"));

        Reel reel;
        try {
            reel = canonicalReelStore.resolveOrCreate(canonical, mediaId, account);
        } catch (DataIntegrityViolationException race) {
            reel = reelRepository.findByCanonicalKey(canonical.key()).orElseThrow(() -> race);
        }

        try {
            ReelIngestionService.IngestionResult result = ingestionService.recordRequest(
                    messageId, senderId, recipientId,
                    objectMapper.writeValueAsString(fullPayload), reel);
            log.info("Ingested request event={} canonicalReel={} duplicate={} jobCreated={}",
                    messageId, reel.getCanonicalKey(), result.duplicateEvent(), result.jobCreated());
        } catch (DataIntegrityViolationException race) {
            // A concurrent request won a single-flight constraint. Meta should retry this event.
            throw race;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not persist Reel request " + messageId, exception);
        }
    }
}
