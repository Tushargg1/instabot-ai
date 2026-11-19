package com.example.instagramreelai.repository;

import com.example.instagramreelai.entity.DiscoveredProspect;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DiscoveredProspectRepository extends JpaRepository<DiscoveredProspect, UUID> {

    List<DiscoveredProspect> findAllByOrderByCreatedAtDesc();

    @Query("""
        SELECT p FROM DiscoveredProspect p
        WHERE (:status IS NULL OR :status = '' OR p.status = :status)
          AND (:flowTag IS NULL OR :flowTag = '' OR LOWER(p.flowTag) = LOWER(:flowTag))
        ORDER BY p.createdAt DESC
    """)
    List<DiscoveredProspect> findFiltered(
            @Param("status") String status,
            @Param("flowTag") String flowTag
    );

    Optional<DiscoveredProspect> findByInstagramUsernameIgnoreCase(String instagramUsername);

    long countByStatus(String status);
}
