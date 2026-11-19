package com.example.instagramreelai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "worker_instances", indexes = {
        @Index(name = "idx_worker_instances_heartbeat", columnList = "last_heartbeat_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkerInstance {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "instance_id", nullable = false, unique = true, length = 128)
    private String instanceId;

    @Column(name = "host_name", nullable = false, length = 255)
    private String hostName;

    @Column(name = "process_id", nullable = false)
    private Long processId;

    @Column(name = "application_name", nullable = false, length = 100)
    private String applicationName;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "last_heartbeat_at", nullable = false)
    private Instant lastHeartbeatAt;

    @Column(name = "stopped_at")
    private Instant stoppedAt;
}