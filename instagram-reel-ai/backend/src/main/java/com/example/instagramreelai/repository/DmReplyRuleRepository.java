package com.example.instagramreelai.repository;

import com.example.instagramreelai.entity.DmReplyRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DmReplyRuleRepository extends JpaRepository<DmReplyRule, UUID> {

    /** All rules for a user, ordered by priority asc then creation time. */
    @Query("SELECT r FROM DmReplyRule r WHERE r.user.id = :userId ORDER BY r.priority ASC, r.createdAt ASC")
    List<DmReplyRule> findByUserIdOrdered(UUID userId);

    /** Only enabled rules for a user (used at reply-time). */
    @Query("SELECT r FROM DmReplyRule r WHERE r.user.id = :userId AND r.enabled = true ORDER BY r.priority ASC, r.createdAt ASC")
    List<DmReplyRule> findEnabledByUserIdOrdered(UUID userId);

    Optional<DmReplyRule> findByIdAndUserId(UUID id, UUID userId);
}
