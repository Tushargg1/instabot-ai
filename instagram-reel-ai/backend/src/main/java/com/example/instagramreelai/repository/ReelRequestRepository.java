package com.example.instagramreelai.repository;

import com.example.instagramreelai.entity.ReelRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReelRequestRepository extends JpaRepository<ReelRequest, UUID> {
    boolean existsByWebhookEventId(UUID webhookEventId);
    boolean existsBySourceAndExternalRequestId(String source, String externalRequestId);
    List<ReelRequest> findByReelId(UUID reelId);
    List<ReelRequest> findByReelIdAndReplyStatusAndInstagramSenderIdIsNotNullOrderByCreatedAtAsc(
            UUID reelId, String replyStatus);
    List<ReelRequest> findByReelIdAndReplyStatusOrderByCreatedAtAsc(UUID reelId, String replyStatus);
    long countByReelId(UUID reelId);
    long countByReelUserId(UUID userId);
}
