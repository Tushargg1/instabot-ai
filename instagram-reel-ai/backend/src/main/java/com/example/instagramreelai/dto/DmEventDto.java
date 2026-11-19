package com.example.instagramreelai.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class DmEventDto {
    private UUID id;
    private String externalEventId;
    private String senderId;
    private String recipientId;
    private String senderUsername;
    private String senderName;
    private String senderProfilePic;
    private Boolean isUserFollowBusiness;
    private String eventType;      // "text", "image", "video", "share", "ig_reel", etc.
    private String messageText;    // extracted from raw_payload > entry[0].messaging[0].message.text
    private String attachmentType; // null for plain text
    private String attachmentUrl;  // media URL if present
    private Boolean processed;
    private Instant receivedAt;
}
