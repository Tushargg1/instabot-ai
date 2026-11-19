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
@Table(name = "reel_resources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReelResource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reel_id", nullable = false, unique = true)
    private Reel reel;

    @Column(name = "resource_url", columnDefinition = "TEXT")
    private String resourceUrl;

    @Column(name = "resource_type")
    private String resourceType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_data", columnDefinition = "jsonb")
    private String extractedData;

    @Column(nullable = false)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "required_action")
    private String requiredAction;

    @Column(name = "action_payload", columnDefinition = "TEXT")
    private String actionPayload;

    @Column(name = "request_count", nullable = false)
    @Builder.Default
    private Long requestCount = 0L;

    @Column(name = "first_found_at")
    private Instant firstFoundAt;

    @Column(name = "last_requested_at")
    private Instant lastRequestedAt;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_refresh_attempt_at")
    private Instant lastRefreshAttemptAt;

    @Column(name = "last_refresh_error", columnDefinition = "TEXT")
    private String lastRefreshError;

    @Column(name = "refresh_failure_count", nullable = false)
    @Builder.Default
    private Integer refreshFailureCount = 0;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "refresh_metadata", columnDefinition = "jsonb")
    private String refreshMetadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
