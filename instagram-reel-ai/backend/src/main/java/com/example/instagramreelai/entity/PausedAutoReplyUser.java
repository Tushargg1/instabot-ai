package com.example.instagramreelai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "paused_auto_reply_users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PausedAutoReplyUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_identifier", nullable = false, unique = true, length = 150)
    private String userIdentifier;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "sender_id", length = 100)
    private String senderId;

    @Column(name = "paused_by", length = 100)
    @Builder.Default
    private String pausedBy = "admin";

    @Column(columnDefinition = "TEXT")
    private String reason;

    @CreationTimestamp
    @Column(name = "paused_at", nullable = false, updatable = false)
    private Instant pausedAt;
}
