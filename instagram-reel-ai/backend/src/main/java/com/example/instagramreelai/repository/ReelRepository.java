package com.example.instagramreelai.repository;

import com.example.instagramreelai.entity.Reel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReelRepository extends JpaRepository<Reel, UUID> {

    Page<Reel> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Reel> findByProcessingStatus(String processingStatus, Pageable pageable);

    Page<Reel> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Reel> findByUserIdAndProcessingStatus(UUID userId, String processingStatus, Pageable pageable);

    Optional<Reel> findByWebhookEventId(UUID webhookEventId);

    Optional<Reel> findByCanonicalKey(String canonicalKey);

    Optional<Reel> findByUserIdAndCanonicalKey(UUID userId, String canonicalKey);

    boolean existsByCanonicalKey(String canonicalKey);

    long countByUserId(UUID userId);

    long countByUserIdAndProcessingStatus(UUID userId, String processingStatus);

    long countByProcessingStatus(String processingStatus);

    @Query("SELECT r FROM Reel r LEFT JOIN FETCH r.webhookEvent LEFT JOIN FETCH r.instagramAccount WHERE r.id = :id")
    Optional<Reel> findByIdWithRelations(@Param("id") UUID id);

    @Query("""
        SELECT r FROM Reel r
        LEFT JOIN r.summary s
        LEFT JOIN r.transcript t
        WHERE r.user.id = :userId
          AND (LOWER(r.creatorUsername) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(r.caption) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(s.title) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(s.summary) LIKE LOWER(CONCAT('%', :query, '%'))
           OR LOWER(t.transcript) LIKE LOWER(CONCAT('%', :query, '%')))
        ORDER BY r.createdAt DESC
        """)
    Page<Reel> searchReelsByUser(@Param("userId") UUID userId, @Param("query") String query, Pageable pageable);

    @Query(value = """
        SELECT r.* FROM reels r
        WHERE r.user_id = :userId
          AND r.search_document @@ websearch_to_tsquery('simple', :query)
        ORDER BY ts_rank_cd(r.search_document, websearch_to_tsquery('simple', :query)) DESC,
                 r.created_at DESC
        """, countQuery = """
        SELECT count(*) FROM reels r
        WHERE r.user_id = :userId
          AND r.search_document @@ websearch_to_tsquery('simple', :query)
        """, nativeQuery = true)
    Page<Reel> searchReelsPostgres(@Param("userId") UUID userId,
                                   @Param("query") String query, Pageable pageable);
}
