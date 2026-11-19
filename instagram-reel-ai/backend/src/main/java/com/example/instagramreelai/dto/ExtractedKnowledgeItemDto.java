package com.example.instagramreelai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedKnowledgeItemDto {
    private UUID id;
    private UUID reelId;
    private String title;
    private String category;
    private String subCategory;
    private String summary;
    private String keyData;
    private String url;
    private List<String> tags;
    private String requesterId;
    private String requesterUsername;
    private String requesterName;
    private String requesterAvatarUrl;
    private String sourceReelUrl;
    private String sourceReelTitle;
    private Instant createdAt;
}
