package com.example.instagramreelai.repository;

import com.example.instagramreelai.entity.InstagramWebhookEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<InstagramWebhookEvent, UUID> {
    Optional<InstagramWebhookEvent> findByExternalEventId(String externalEventId);
    boolean existsByExternalEventId(String externalEventId);

    /** All events, newest first. */
    Page<InstagramWebhookEvent> findAllByOrderByReceivedAtDesc(Pageable pageable);

    /** Events from a specific sender. */
    @Query("SELECT e FROM InstagramWebhookEvent e WHERE e.senderId = :senderId ORDER BY e.receivedAt DESC")
    Page<InstagramWebhookEvent> findBySenderIdOrderByReceivedAtDesc(@Param("senderId") String senderId, Pageable pageable);
}
