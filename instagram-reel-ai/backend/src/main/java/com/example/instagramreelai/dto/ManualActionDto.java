package com.example.instagramreelai.dto;

import com.example.instagramreelai.entity.ManualActionStatus;

import java.time.Instant;
import java.util.UUID;

public record ManualActionDto(
        UUID id,
        UUID reelResourceId,
        UUID reelId,
        String actionType,
        String actionPayload,
        ManualActionStatus status,
        UUID claimantUserId,
        String claimantUsername,
        Instant claimedAt,
        Instant leaseExpiresAt,
        Instant completedAt,
        Instant failedAt,
        String result,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
}
