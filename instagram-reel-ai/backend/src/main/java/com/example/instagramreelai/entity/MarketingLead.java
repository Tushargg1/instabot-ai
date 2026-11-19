package com.example.instagramreelai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "marketing_leads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketingLead {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "instagram_username", nullable = false, length = 100)
    private String instagramUsername;

    @Column(name = "instagram_sender_id", length = 100)
    private String instagramSenderId;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(columnDefinition = "TEXT")
    private String context;

    @Column(length = 100)
    @Builder.Default
    private String tag = "General";

    @Column(name = "product_offer", nullable = false, columnDefinition = "TEXT")
    private String productOffer;

    @Column(name = "sales_goal")
    @Builder.Default
    private String salesGoal = "Book call / Buy product";

    @Column(name = "first_message", columnDefinition = "TEXT")
    private String firstMessage;

    @Column(name = "outreach_sent_at")
    private Instant outreachSentAt;

    @Column(name = "last_interaction_at")
    private Instant lastInteractionAt;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "PENDING"; // PENDING, OUTREACHED, IN_CONVERSATION, CONVERTED, CLOSED

    @Column(name = "conversation_history", columnDefinition = "TEXT")
    @Builder.Default
    private String conversationHistory = "[]";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
