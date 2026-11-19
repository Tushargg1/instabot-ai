package com.example.instagramreelai.repository;

import com.example.instagramreelai.entity.ExtractedKnowledgeItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExtractedKnowledgeItemRepository extends JpaRepository<ExtractedKnowledgeItem, UUID> {

    List<ExtractedKnowledgeItem> findByReelId(UUID reelId);

    void deleteByReelId(UUID reelId);

    @Query("""
        SELECT e FROM ExtractedKnowledgeItem e
        WHERE (:category IS NULL OR :category = '' OR LOWER(e.category) = LOWER(:category))
          AND (:subCategory IS NULL OR :subCategory = '' OR LOWER(e.subCategory) = LOWER(:subCategory))
          AND (:query IS NULL OR :query = '' OR (
                LOWER(e.title) LIKE LOWER(CONCAT('%', :query, '%')) OR
                LOWER(e.summary) LIKE LOWER(CONCAT('%', :query, '%')) OR
                LOWER(e.keyData) LIKE LOWER(CONCAT('%', :query, '%')) OR
                LOWER(e.category) LIKE LOWER(CONCAT('%', :query, '%')) OR
                LOWER(e.subCategory) LIKE LOWER(CONCAT('%', :query, '%')) OR
                LOWER(e.requesterUsername) LIKE LOWER(CONCAT('%', :query, '%')) OR
                LOWER(e.requesterName) LIKE LOWER(CONCAT('%', :query, '%')) OR
                LOWER(e.sourceReelTitle) LIKE LOWER(CONCAT('%', :query, '%'))
          ))
        ORDER BY e.createdAt DESC
    """)
    Page<ExtractedKnowledgeItem> search(
            @Param("query") String query,
            @Param("category") String category,
            @Param("subCategory") String subCategory,
            Pageable pageable);

    @Query("SELECT e.category, COUNT(e) FROM ExtractedKnowledgeItem e GROUP BY e.category ORDER BY COUNT(e) DESC")
    List<Object[]> countByCategory();

    @Query("SELECT e.category, e.subCategory, COUNT(e) FROM ExtractedKnowledgeItem e WHERE e.subCategory IS NOT NULL GROUP BY e.category, e.subCategory ORDER BY COUNT(e) DESC")
    List<Object[]> countByCategoryAndSubCategory();
}
