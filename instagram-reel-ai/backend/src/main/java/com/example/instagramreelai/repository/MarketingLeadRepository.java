package com.example.instagramreelai.repository;

import com.example.instagramreelai.entity.MarketingLead;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MarketingLeadRepository extends JpaRepository<MarketingLead, UUID> {

    Optional<MarketingLead> findByInstagramSenderId(String instagramSenderId);

    Optional<MarketingLead> findByInstagramUsernameIgnoreCase(String instagramUsername);

    @Query("""
        SELECT l FROM MarketingLead l
        WHERE (:status IS NULL OR :status = '' OR l.status = :status)
          AND (:tag IS NULL OR :tag = '' OR l.tag = :tag)
          AND (:query IS NULL OR :query = '' OR (
              LOWER(l.instagramUsername) LIKE LOWER(CONCAT('%', :query, '%')) OR
              LOWER(l.contactName) LIKE LOWER(CONCAT('%', :query, '%')) OR
              LOWER(l.context) LIKE LOWER(CONCAT('%', :query, '%')) OR
              LOWER(l.productOffer) LIKE LOWER(CONCAT('%', :query, '%'))
          ))
    """)
    Page<MarketingLead> searchLeads(
            @Param("query") String query,
            @Param("status") String status,
            @Param("tag") String tag,
            Pageable pageable
    );

    long countByStatus(String status);

    @Query("SELECT DISTINCT l.tag FROM MarketingLead l WHERE l.tag IS NOT NULL ORDER BY l.tag ASC")
    List<String> findDistinctTags();
}
