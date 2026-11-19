package com.example.instagramreelai.repository;

import com.example.instagramreelai.entity.ReelRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReelRequestRepository extends JpaRepository<ReelRequest, UUID> {
    boolean existsByWebhookEventId(UUID webhookEventId);
    boolean existsBySourceAndExternalRequestId(String source, String externalRequestId);
    List<ReelRequest> findByReelId(UUID reelId);
    Optional<ReelRequest> findTopByInstagramSenderIdOrderByCreatedAtDesc(String instagramSenderId);
    List<ReelRequest> findByReelIdAndReplyStatusAndInstagramSenderIdIsNotNullOrderByCreatedAtAsc(
            UUID reelId, String replyStatus);
    List<ReelRequest> findByReelIdAndReplyStatusOrderByCreatedAtAsc(UUID reelId, String replyStatus);
    long countByReelId(UUID reelId);
    long countByReelUserId(UUID userId);

    @Query("SELECT r.instagramSenderId, COUNT(r), MAX(r.createdAt) FROM ReelRequest r WHERE r.instagramSenderId IS NOT NULL GROUP BY r.instagramSenderId ORDER BY COUNT(r) DESC")
    List<Object[]> findSuperfansRaw();

    @Query("SELECT COUNT(DISTINCT r.instagramSenderId) FROM ReelRequest r")
    long countDistinctRequesters();

    long countByWasFollowingInitiallyFalse();

    long countByWasFollowingInitiallyFalseAndConvertedToFollowerTrue();
}
