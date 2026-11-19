package com.example.instagramreelai.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ReelListDto {
    private UUID id;
    private String title;
    private String summaryExcerpt;
    private String creatorUsername;
    private String processingStatus;
    private List<String> topics;
    private Instant savedAt;
    private Instant createdAt;
}
