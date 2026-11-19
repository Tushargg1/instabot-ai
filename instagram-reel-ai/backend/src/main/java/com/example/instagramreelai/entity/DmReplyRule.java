package com.example.instagramreelai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A single DM auto-reply rule owned by an AppUser.
 *
 * Match logic:
 *   KEYWORD  – reply only when the incoming message contains {@code matchValue} (case-insensitive)
 *   REGEX    – reply when the message matches the regex in {@code matchValue}
 *   ALL      – reply to every incoming DM (matchValue is ignored)
 *
 * Rules are evaluated in ascending {@code priority} order; the first match wins.
 */
@Entity
@Table(name = "dm_reply_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DmReplyRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /** Human-readable name, e.g. "Welcome greeting" */
    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false)
    private MatchType matchType;

    /** The keyword / regex string; ignored for ALL rules. */
    @Column(name = "match_value", columnDefinition = "TEXT")
    private String matchValue;

    /** The text sent back to the DM sender. Supports {sender_id} placeholder. */
    @Column(name = "reply_text", nullable = false, columnDefinition = "TEXT")
    private String replyText;

    /** Lower number = evaluated first. */
    @Builder.Default
    @Column(nullable = false)
    private Integer priority = 100;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum MatchType {
        KEYWORD,
        REGEX,
        ALL
    }
}
