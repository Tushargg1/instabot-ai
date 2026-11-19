package com.example.instagramreelai.repository;

import com.example.instagramreelai.entity.CapturedLead;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CapturedLeadRepository extends JpaRepository<CapturedLead, UUID> {

    Optional<CapturedLead> findByEmailAndInstagramSenderId(String email, String instagramSenderId);

    boolean existsByInstagramSenderId(String instagramSenderId);

    @Query("""
        SELECT l FROM CapturedLead l
        WHERE (:query IS NULL OR :query = '' OR (
            LOWER(l.email) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(l.instagramUsername) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(l.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR
            LOWER(l.sourceReelTitle) LIKE LOWER(CONCAT('%', :query, '%'))
        ))
    """)
    Page<CapturedLead> search(@Param("query") String query, Pageable pageable);
}
