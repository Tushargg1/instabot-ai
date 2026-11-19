package com.example.instagramreelai.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ProcessingJobDto {
    private UUID id;
    private UUID reelId;
    private String jobType;
    private String status;
    private String stage;
    private Integer attempts;
    private Integer maxAttempts;
    private String lastErrorCode;
    private String lastErrorMessage;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;
}
