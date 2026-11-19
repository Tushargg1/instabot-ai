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

        String senderUsername = null;
        String senderName = null;
        String senderProfilePic = null;
        Boolean isFollowing = null;

        if (event.getSenderId() != null && !event.getSenderId().isBlank()) {
            var profileOpt = userProfileService.getProfile(event.getSenderId());
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
}
