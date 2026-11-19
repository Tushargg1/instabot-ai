package com.example.instagramreelai.repository;

import com.example.instagramreelai.entity.ReelResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReelResourceRepository extends JpaRepository<ReelResource, UUID> {
    Optional<ReelResource> findByReelId(UUID reelId);
    Optional<ReelResource> findByIdAndReelUserId(UUID id, UUID userId);
    Page<ReelResource> findByReelUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    List<ReelResource> findByExpiresAtBeforeOrderByExpiresAtAsc(Instant expiresAt, Pageable pageable);
    long countByReelUserId(UUID userId);
    long countByReelUserIdAndStatus(UUID userId, String status);
}
