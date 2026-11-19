package com.example.instagramreelai.service;

import com.example.instagramreelai.dto.DmEventDto;
import com.example.instagramreelai.entity.InstagramWebhookEvent;
import com.example.instagramreelai.repository.WebhookEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DmInboxService {

    private final WebhookEventRepository eventRepository;
    private final ObjectMapper objectMapper;
    private final InstagramUserProfileService userProfileService;
    private final com.example.instagramreelai.config.AppProperties appProperties;
    private final com.example.instagramreelai.instagram.InstagramClient instagramClient;
    private final com.example.instagramreelai.repository.InstagramAccountRepository accountRepository;
    private final EncryptionService encryptionService;

    public Page<DmEventDto> listEvents(int page, int size, String senderId) {
        Pageable pageable = PageRequest.of(page, size);
        Page<InstagramWebhookEvent> events = (senderId != null && !senderId.isBlank())
                ? eventRepository.findBySenderIdOrderByReceivedAtDesc(senderId, pageable)
                : eventRepository.findAllByOrderByReceivedAtDesc(pageable);

        return events.map(this::toDto);
    }

    private DmEventDto toDto(InstagramWebhookEvent event) {
        String messageText = null;
        String attachmentType = null;
        String attachmentUrl = null;

        // Parse the raw payload to extract message text and attachments
        try {
            JsonNode root = objectMapper.readTree(event.getRawPayload());
            // The full payload wraps: { "entry": [{ "messaging": [{ "message": {...} }] }] }
            JsonNode messaging = root.path("entry").path(0).path("messaging").path(0);
            JsonNode message = messaging.path("message");

            messageText = message.path("text").asText(null);
            if (messageText != null && messageText.isBlank()) messageText = null;

            JsonNode attachments = message.path("attachments");
            if (attachments.isArray() && !attachments.isEmpty()) {
                JsonNode first = attachments.get(0);
                attachmentType = first.path("type").asText(null);
                JsonNode payload = first.path("payload");
                attachmentUrl = payload.path("url").asText(null);
                if (attachmentUrl == null || attachmentUrl.isBlank()) {
                    attachmentUrl = payload.path("reel_video_id").asText(null);
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse raw payload for event {}: {}", event.getId(), e.getMessage());
        }

        String accountId = appProperties.getInstagram().getAccountId();
        boolean isOutgoing = accountId != null && accountId.equals(event.getSenderId());
        String contactId = isOutgoing ? event.getRecipientId() : event.getSenderId();

        String senderUsername = null;
        String senderName = null;
        String senderProfilePic = null;
        Boolean isFollowing = null;

        String targetForProfile = (contactId != null && !contactId.isBlank()) ? contactId : event.getSenderId();
        if (targetForProfile != null && !targetForProfile.isBlank() && !targetForProfile.equals(accountId)) {
            var profileOpt = userProfileService.getProfile(targetForProfile);
            if (profileOpt.isPresent()) {
                var p = profileOpt.get();
                senderUsername = p.getUsername();
                senderName = p.getName();
                senderProfilePic = p.getProfilePic();
                isFollowing = p.getIsUserFollowBusiness();
            }
        }

        return DmEventDto.builder()
                .id(event.getId())
                .externalEventId(event.getExternalEventId())
                .senderId(event.getSenderId())
                .recipientId(event.getRecipientId())
                .contactId(contactId)
                .outgoing(isOutgoing)
                .senderUsername(senderUsername)
                .senderName(senderName)
                .senderProfilePic(senderProfilePic)
                .isUserFollowBusiness(isFollowing)
                .eventType(event.getEventType())
                .messageText(messageText)
                .attachmentType(attachmentType)
                .attachmentUrl(attachmentUrl)
                .processed(event.getProcessed())
                .receivedAt(event.getReceivedAt())
                .build();
    }

    public boolean sendManualReply(String recipientId, String messageText) {
        String accountId = appProperties.getInstagram().getAccountId();
        String accessToken = appProperties.getInstagram().getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            var accOpt = accountRepository.findByInstagramAccountId(accountId);
            if (accOpt.isPresent() && accOpt.get().getAccessTokenEncrypted() != null) {
                try {
                    accessToken = encryptionService.decrypt(accOpt.get().getAccessTokenEncrypted());
                } catch (Exception e) {
                    log.error("Failed to decrypt token", e);
                }
            }
        }

        if (accessToken == null || accessToken.isBlank()) {
            log.error("No access token available to send manual reply");
            return false;
        }

        boolean sent = instagramClient.sendReply(accountId, recipientId, messageText, accessToken);
        if (sent) {
            try {
                var event = new InstagramWebhookEvent();
                event.setExternalEventId("manual_" + java.util.UUID.randomUUID());
                event.setSenderId(accountId);
                event.setRecipientId(recipientId);
                event.setEventType("text");
                event.setRawPayload(objectMapper.writeValueAsString(java.util.Map.of(
                        "entry", java.util.List.of(java.util.Map.of(
                                "messaging", java.util.List.of(java.util.Map.of(
                                        "message", java.util.Map.of("text", messageText)
                                ))
                        ))
                )));
                event.setReceivedAt(java.time.Instant.now());
                event.setProcessed(true);
                eventRepository.save(event);
            } catch (Exception ex) {
                log.warn("Failed to persist manual sent message: {}", ex.getMessage());
            }
        }
        return sent;
    }
}
