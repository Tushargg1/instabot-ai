package com.example.instagramreelai.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ReelDto {
    private UUID id;
    private String sourceUrl;
    private String externalMediaId;
    private String creatorUsername;
    private String caption;
    private String processingStatus;
    private Instant savedAt;
    private Instant createdAt;

    // Summary fields
    private String title;
    private String summaryText;
    private List<String> keyPoints;
    private List<String> actionItems;
    private List<String> topics;
    private List<String> toolsOrProducts;
    private List<String> peopleOrCompanies;
    private List<String> claimsToVerify;
    private String language;
    private Double confidence;

    // Transcript
    private String transcript;
    private Double durationSeconds;
}
