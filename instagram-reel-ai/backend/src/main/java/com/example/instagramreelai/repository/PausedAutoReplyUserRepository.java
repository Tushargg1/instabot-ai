package com.example.instagramreelai.repository;

import com.example.instagramreelai.entity.PausedAutoReplyUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PausedAutoReplyUserRepository extends JpaRepository<PausedAutoReplyUser, UUID> {

    Optional<PausedAutoReplyUser> findByUserIdentifierIgnoreCase(String userIdentifier);

    boolean existsByUserIdentifierIgnoreCase(String userIdentifier);

    @Query("""
        SELECT p FROM PausedAutoReplyUser p
        WHERE LOWER(p.userIdentifier) = LOWER(:identifier)
           OR (p.username IS NOT NULL AND LOWER(p.username) = LOWER(:identifier))
           OR (p.senderId IS NOT NULL AND p.senderId = :identifier)
    """)
    List<PausedAutoReplyUser> findAnyMatching(@Param("identifier") String identifier);

    void deleteByUserIdentifierIgnoreCase(String userIdentifier);
}
