package com.example.instagramreelai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "public_submission_tokens", indexes = {
        @Index(name = "idx_public_submission_tokens_expiry", columnList = "expires_at")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicSubmissionToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "token_digest", nullable = false, unique = true, length = 64)
    private String tokenDigest;

    @Column(name = "public_label", length = 120)
    private String publicLabel;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}