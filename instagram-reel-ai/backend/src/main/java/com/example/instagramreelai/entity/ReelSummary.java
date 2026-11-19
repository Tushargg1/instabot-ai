package com.example.instagramreelai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reel_summaries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReelSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reel_id", nullable = false, unique = true)
    private Reel reel;

    @Column
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_points", columnDefinition = "jsonb")
    private String keyPoints;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action_items", columnDefinition = "jsonb")
    private String actionItems;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "topics", columnDefinition = "jsonb")
    private String topics;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tools_or_products", columnDefinition = "jsonb")
    private String toolsOrProducts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "people_or_companies", columnDefinition = "jsonb")
    private String peopleOrCompanies;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "claims_to_verify", columnDefinition = "jsonb")
    private String claimsToVerify;

    @Column
    private String language;

    @Column
    private BigDecimal confidence;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
