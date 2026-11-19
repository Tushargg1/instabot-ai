package com.example.instagramreelai.dto;

import java.time.Instant;
import java.util.UUID;

public record ReelResourceDto(
        UUID id,
        UUID reelId,
        String resourceUrl,
        String resourceType,
        String extractedData,
        String status,
        String requiredAction,
        String actionPayload,
        Long requestCount,
        Instant firstFoundAt,
        Instant lastRequestedAt,
        Instant lastVerifiedAt,
        Instant expiresAt,
        Instant lastRefreshAttemptAt,
        String lastRefreshError,
        Integer refreshFailureCount,
        String refreshMetadata,
        Instant createdAt,
        Instant updatedAt) {
}
