package com.example.instagramreelai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "extracted_knowledge_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtractedKnowledgeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reel_id", nullable = false)
    private Reel reel;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(name = "sub_category", length = 100)
    private String subCategory;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "key_data", columnDefinition = "TEXT")
    private String keyData;

    @Column(columnDefinition = "TEXT")
    private String url;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    private String tags;

    @Column(name = "requester_id", length = 100)
    private String requesterId;

    @Column(name = "requester_username", length = 100)
    private String requesterUsername;

    @Column(name = "requester_name", length = 255)
    private String requesterName;

    @Column(name = "requester_avatar_url", columnDefinition = "TEXT")
    private String requesterAvatarUrl;

    @Column(name = "source_reel_url", columnDefinition = "TEXT")
    private String sourceReelUrl;

    @Column(name = "source_reel_title", length = 255)
    private String sourceReelTitle;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
