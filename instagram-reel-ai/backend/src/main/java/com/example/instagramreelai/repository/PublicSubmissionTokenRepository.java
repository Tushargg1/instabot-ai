package com.example.instagramreelai.repository;

import com.example.instagramreelai.entity.PublicSubmissionToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PublicSubmissionTokenRepository extends JpaRepository<PublicSubmissionToken, UUID> {
    Optional<PublicSubmissionToken> findByTokenDigest(String tokenDigest);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PublicSubmissionToken t SET t.consumedAt = :now " +
            "WHERE t.id = :id AND t.consumedAt IS NULL AND t.expiresAt > :now")
    int consumeIfValid(@Param("id") UUID id, @Param("now") Instant now);
}