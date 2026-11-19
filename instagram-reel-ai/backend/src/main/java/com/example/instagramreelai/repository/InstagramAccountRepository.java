package com.example.instagramreelai.repository;

import com.example.instagramreelai.entity.InstagramAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstagramAccountRepository extends JpaRepository<InstagramAccount, UUID> {
    Optional<InstagramAccount> findByInstagramAccountId(String instagramAccountId);
    Optional<InstagramAccount> findByInstagramAccountIdAndActiveTrue(String instagramAccountId);
    Optional<InstagramAccount> findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(UUID userId);
    Optional<InstagramAccount> findFirstByActiveTrueOrderByUpdatedAtDesc();
    List<InstagramAccount> findByUserIdAndActiveTrue(UUID userId);
    List<InstagramAccount> findByActiveTrue();
}
