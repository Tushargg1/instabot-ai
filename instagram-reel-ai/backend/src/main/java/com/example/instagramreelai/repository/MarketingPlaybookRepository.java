package com.example.instagramreelai.repository;

import com.example.instagramreelai.entity.MarketingPlaybook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MarketingPlaybookRepository extends JpaRepository<MarketingPlaybook, UUID> {

    List<MarketingPlaybook> findAllByOrderByCreatedAtDesc();

    List<MarketingPlaybook> findByTagIgnoreCase(String tag);

    Optional<MarketingPlaybook> findTopByTagIgnoreCaseOrderByCreatedAtDesc(String tag);
}
