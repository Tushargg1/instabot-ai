package com.example.instagramreelai.repository;

import com.example.instagramreelai.entity.InstagramOAuthState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InstagramOAuthStateRepository extends JpaRepository<InstagramOAuthState, UUID> {
    Optional<InstagramOAuthState> findByStateHashAndConsumedAtIsNull(String stateHash);
}
