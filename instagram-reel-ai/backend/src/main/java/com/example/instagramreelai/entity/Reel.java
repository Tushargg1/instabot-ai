package com.example.instagramreelai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "canonical_key", nullable = false, length = 500)
    private String canonicalKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instagram_account_id")
    private InstagramAccount instagramAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "webhook_event_id")
    private InstagramWebhookEvent webhookEvent;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "external_media_id")
    private String externalMediaId;

    @Column(name = "creator_username")
    private String creatorUsername;

    @Column
    private String caption;

    @Column(name = "processing_status", nullable = false)
    private String processingStatus;

    @Column(name = "media_mime_type")
    private String mediaMimeType;

    @Column(name = "media_size_bytes")
    private Long mediaSizeBytes;

    @Column(name = "request_count")
    @Builder.Default
    private Long requestCount = 0L;

    @Column(name = "last_requested_at")
    private Instant lastRequestedAt;

    @Column(name = "saved_at", nullable = false)
    @Builder.Default
    private Instant savedAt = Instant.now();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToOne(mappedBy = "reel", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private ReelTranscript transcript;

    @OneToOne(mappedBy = "reel", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private ReelSummary summary;
}
