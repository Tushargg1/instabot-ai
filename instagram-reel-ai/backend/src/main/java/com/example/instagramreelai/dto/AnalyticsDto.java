package com.example.instagramreelai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsDto {

    private ConversionStats conversionStats;
    private List<TopicShare> trendingTopics;
    private List<Superfan> superfans;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversionStats {
        private long totalRequests;
        private long totalUniqueUsers;
        private long nonFollowerTeasersSent;
        private long convertedToFollowers;
        private double conversionRatePercentage;
        private long totalLeadsCaptured;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicShare {
        private String category;
        private String subCategory;
        private long count;
        private double percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Superfan {
        private int rank;
        private String senderId;
        private String username;
        private String name;
        private String avatarUrl;
        private boolean isFollowing;
        private long totalRequests;
        private String lastActiveAt;
    }
}
