package com.example.instagramreelai.dto;

import java.time.Instant;
import java.util.UUID;

public record WorkerInstanceResponse(
        UUID id,
        String instanceId,
        String hostName,
        long processId,
        String applicationName,
        String state,
        Instant startedAt,
        Instant registeredAt,
        Instant lastHeartbeatAt,
        Instant stoppedAt) {
}