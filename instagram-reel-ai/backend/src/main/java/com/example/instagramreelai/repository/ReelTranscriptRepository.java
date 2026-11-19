package com.example.instagramreelai.repository;

import com.example.instagramreelai.entity.ReelTranscript;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReelTranscriptRepository extends JpaRepository<ReelTranscript, UUID> {
    Optional<ReelTranscript> findByReelId(UUID reelId);
}
