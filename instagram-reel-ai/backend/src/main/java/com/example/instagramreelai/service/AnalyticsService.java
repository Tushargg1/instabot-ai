package com.example.instagramreelai.service;

import com.example.instagramreelai.dto.AnalyticsDto;
import com.example.instagramreelai.instagram.InstagramUserProfile;
import com.example.instagramreelai.repository.CapturedLeadRepository;
import com.example.instagramreelai.repository.ExtractedKnowledgeItemRepository;
import com.example.instagramreelai.repository.ReelRepository;
import com.example.instagramreelai.repository.ReelRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ReelRequestRepository requestRepository;
    private final ReelRepository reelRepository;
    private final CapturedLeadRepository leadRepository;
    private final ExtractedKnowledgeItemRepository itemRepository;
    private final InstagramUserProfileService userProfileService;

    @Transactional(readOnly = true)
    public AnalyticsDto getAnalytics() {
        // 1. Conversion metrics
        long totalRequests = requestRepository.count();
        long totalUniqueUsers = requestRepository.countDistinctRequesters();
        long nonFollowerTeasers = requestRepository.countByWasFollowingInitiallyFalse();
        long convertedFollowers = requestRepository.countByWasFollowingInitiallyFalseAndConvertedToFollowerTrue();
        double conversionRate = nonFollowerTeasers > 0
                ? ((double) convertedFollowers / nonFollowerTeasers) * 100.0
                : 0.0;
        long totalLeads = leadRepository.count();

        AnalyticsDto.ConversionStats conversionStats = AnalyticsDto.ConversionStats.builder()
                .totalRequests(totalRequests)
                .totalUniqueUsers(totalUniqueUsers)
                .nonFollowerTeasersSent(nonFollowerTeasers)
                .convertedToFollowers(convertedFollowers)
                .conversionRatePercentage(Math.round(conversionRate * 10.0) / 10.0)
                .totalLeadsCaptured(totalLeads)
                .build();

        // 2. Trending Topics
        List<Object[]> rawSubCounts = itemRepository.countByCategoryAndSubCategory();
        long totalItems = itemRepository.count();
        List<AnalyticsDto.TopicShare> trending = new ArrayList<>();

        for (Object[] row : rawSubCounts) {
            String cat = (String) row[0];
            String sub = (String) row[1];
            long count = ((Number) row[2]).longValue();
            double pct = totalItems > 0 ? (count * 100.0 / totalItems) : 0.0;
            trending.add(AnalyticsDto.TopicShare.builder()
                    .category(cat)
                    .subCategory(sub)
                    .count(count)
                    .percentage(Math.round(pct * 10.0) / 10.0)
                    .build());
        }

        // 3. Superfans Leaderboard
        List<Object[]> rawSuperfans = requestRepository.findSuperfansRaw();
        List<AnalyticsDto.Superfan> superfans = new ArrayList<>();
        int rank = 1;

        for (Object[] row : rawSuperfans) {
            if (rank > 15) break;
            String senderId = (String) row[0];
            long count = ((Number) row[1]).longValue();
            Instant lastActive = (Instant) row[2];

            String username = null;
            String name = null;
            String avatar = null;
            boolean isFollowing = false;

            Optional<InstagramUserProfile> profileOpt = userProfileService.getProfile(senderId);
            if (profileOpt.isPresent()) {
                InstagramUserProfile p = profileOpt.get();
                username = p.getUsername();
                name = p.getName();
                avatar = p.getProfilePic();
                isFollowing = Boolean.TRUE.equals(p.getIsUserFollowBusiness());
            }

            superfans.add(AnalyticsDto.Superfan.builder()
                    .rank(rank++)
                    .senderId(senderId)
                    .username(username)
                    .name(name)
                    .avatarUrl(avatar)
                    .isFollowing(isFollowing)
                    .totalRequests(count)
                    .lastActiveAt(lastActive != null ? lastActive.toString() : null)
                    .build());
        }

        return AnalyticsDto.builder()
                .conversionStats(conversionStats)
                .trendingTopics(trending)
                .superfans(superfans)
                .build();
    }
}
