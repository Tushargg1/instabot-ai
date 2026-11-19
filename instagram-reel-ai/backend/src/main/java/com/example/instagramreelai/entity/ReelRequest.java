package com.example.instagramreelai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reel_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReelRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reel_id", nullable = false)
    private Reel reel;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "webhook_event_id", unique = true)
    private InstagramWebhookEvent webhookEvent;

    @Column(name = "source", nullable = false, length = 50)
    @Builder.Default
    private String source = "INSTAGRAM_WEBHOOK";

    @Column(name = "external_request_id", length = 255)
    private String externalRequestId;

    @Column(name = "instagram_sender_id")
    private String instagramSenderId;

    @Column(name = "instagram_recipient_id")
    private String instagramRecipientId;

    @Column(nullable = false)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "reply_status", nullable = false)
    @Builder.Default
    private String replyStatus = "PENDING";

    @Column(name = "was_following_initially")
    @Builder.Default
    private Boolean wasFollowingInitially = true;

    @Column(name = "converted_to_follower")
    @Builder.Default
    private Boolean convertedToFollower = false;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "replied_at")
    private Instant repliedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
