package com.example.instagramreelai.repository;

import com.example.instagramreelai.entity.ReelSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReelSummaryRepository extends JpaRepository<ReelSummary, UUID> {
    Optional<ReelSummary> findByReelId(UUID reelId);
}
