package com.example.instagramreelai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "discovered_prospects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiscoveredProspect {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "flow_tag", nullable = false, length = 100)
    private String flowTag;

    @Column(name = "instagram_username", nullable = false, length = 100)
    private String instagramUsername;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Column(name = "profile_bio", columnDefinition = "TEXT")
    private String profileBio;

    @Column(name = "niche_signals", columnDefinition = "TEXT")
    private String nicheSignals;

    @Column(name = "estimated_followers", length = 50)
    private String estimatedFollowers;

    @Column(name = "proposed_intro_text", nullable = false, columnDefinition = "TEXT")
    private String proposedIntroText;

    @Column(name = "match_score")
    @Builder.Default
    private Integer matchScore = 85;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "DISCOVERED"; // DISCOVERED, APPROVED, REJECTED

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
