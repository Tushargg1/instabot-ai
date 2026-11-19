package com.example.instagramreelai.service;

import com.example.instagramreelai.ai.SummaryService;
import com.example.instagramreelai.dto.AiSummaryResult;
import com.example.instagramreelai.dto.CategoryCountDto;
import com.example.instagramreelai.dto.ExtractedKnowledgeItemDto;
import com.example.instagramreelai.entity.ExtractedKnowledgeItem;
import com.example.instagramreelai.entity.Reel;
import com.example.instagramreelai.entity.ReelRequest;
import com.example.instagramreelai.entity.ReelSummary;
import com.example.instagramreelai.instagram.InstagramUserProfile;
import com.example.instagramreelai.repository.ExtractedKnowledgeItemRepository;
import com.example.instagramreelai.repository.ReelRepository;
import com.example.instagramreelai.repository.ReelRequestRepository;
import com.example.instagramreelai.repository.ReelSummaryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final ExtractedKnowledgeItemRepository itemRepository;
    private final ReelRequestRepository requestRepository;
    private final ReelRepository reelRepository;
    private final ReelSummaryRepository summaryRepository;
    private final InstagramUserProfileService userProfileService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveExtractedItems(Reel reel, AiSummaryResult result) {
        if (result == null || result.getExtractedItems() == null || result.getExtractedItems().isEmpty()) {
            return;
        }

        // Determine requester details from pending/completed requests for this reel
        String senderId = null;
        String senderUsername = null;
        String senderName = null;
        String senderAvatar = null;

        List<ReelRequest> requests = requestRepository.findByReelId(reel.getId());
        if (!requests.isEmpty()) {
            ReelRequest firstReq = requests.get(0);
            senderId = firstReq.getInstagramSenderId();
            if (senderId != null && !senderId.isBlank()) {
                Optional<InstagramUserProfile> profileOpt = userProfileService.getProfile(senderId);
                if (profileOpt.isPresent()) {
                    InstagramUserProfile p = profileOpt.get();
                    senderUsername = p.getUsername();
                    senderName = p.getName();
                    senderAvatar = p.getProfilePic();
                }
            }
        }

        String sourceReelTitle = result.getTitle() != null && !result.getTitle().isBlank()
                ? result.getTitle()
                : (reel.getCaption() != null && !reel.getCaption().isBlank()
                    ? (reel.getCaption().length() > 60 ? reel.getCaption().substring(0, 57) + "..." : reel.getCaption())
                    : "Instagram Reel");

        for (ExtractedKnowledgeItemDto dto : result.getExtractedItems()) {
            try {
                String tagsJson = null;
                if (dto.getTags() != null && !dto.getTags().isEmpty()) {
                    tagsJson = objectMapper.writeValueAsString(dto.getTags());
                } else if (result.getTopics() != null && !result.getTopics().isEmpty()) {
                    tagsJson = objectMapper.writeValueAsString(result.getTopics());
                }

                ExtractedKnowledgeItem item = ExtractedKnowledgeItem.builder()
                        .reel(reel)
                        .title(dto.getTitle() != null && !dto.getTitle().isBlank() ? dto.getTitle() : "Key Resource")
                        .category(dto.getCategory() != null && !dto.getCategory().isBlank() ? dto.getCategory() : "Tools")
                        .subCategory(dto.getSubCategory() != null && !dto.getSubCategory().isBlank() ? dto.getSubCategory() : "General")
                        .summary(dto.getSummary() != null && !dto.getSummary().isBlank() ? dto.getSummary() : result.getSummary())
                        .keyData(dto.getKeyData())
                        .url(dto.getUrl())
                        .tags(tagsJson)
                        .requesterId(senderId)
                        .requesterUsername(senderUsername)
                        .requesterName(senderName)
                        .requesterAvatarUrl(senderAvatar)
                        .sourceReelUrl(reel.getSourceUrl())
                        .sourceReelTitle(sourceReelTitle)
                        .build();

                itemRepository.save(item);
            } catch (Exception e) {
                log.warn("Failed to persist knowledge item {}: {}", dto.getTitle(), e.getMessage());
            }
        }
        log.info("Saved {} knowledge items for reel={}", result.getExtractedItems().size(), reel.getId());
    }

    @Transactional(readOnly = true)
    public Page<ExtractedKnowledgeItemDto> search(String query, String category, String subCategory, Pageable pageable) {
        String cleanQuery = (query != null && !query.isBlank()) ? query.trim() : null;
        String cleanCategory = (category != null && !category.equalsIgnoreCase("all") && !category.isBlank()) ? category.trim() : null;
        String cleanSubCategory = (subCategory != null && !subCategory.equalsIgnoreCase("all") && !subCategory.isBlank()) ? subCategory.trim() : null;

        return itemRepository.search(cleanQuery, cleanCategory, cleanSubCategory, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<CategoryCountDto> getCategoriesHierarchy() {
        List<Object[]> categoryCounts = itemRepository.countByCategory();
        List<Object[]> subCounts = itemRepository.countByCategoryAndSubCategory();

        // Map subcategories by category
        Map<String, List<CategoryCountDto.SubCategoryCountDto>> subMap = new HashMap<>();
        for (Object[] row : subCounts) {
            String cat = (String) row[0];
            String sub = (String) row[1];
            long count = ((Number) row[2]).longValue();
            subMap.computeIfAbsent(cat, k -> new ArrayList<>())
                    .add(CategoryCountDto.SubCategoryCountDto.builder()
                            .name(sub)
                            .count(count)
                            .build());
        }

        List<CategoryCountDto> result = new ArrayList<>();
        for (Object[] row : categoryCounts) {
            String cat = (String) row[0];
            long count = ((Number) row[1]).longValue();
            result.add(CategoryCountDto.builder()
                    .name(cat)
                    .count(count)
                    .subCategories(subMap.getOrDefault(cat, Collections.emptyList()))
                    .build());
        }

        return result;
    }

    @Transactional
    public int backfillFromSummaries() {
        List<Reel> reels = reelRepository.findAll();
        int created = 0;

        for (Reel reel : reels) {
            List<ExtractedKnowledgeItem> existing = itemRepository.findByReelId(reel.getId());
            if (!existing.isEmpty()) continue;

            Optional<ReelSummary> summaryOpt = summaryRepository.findByReelId(reel.getId());
            if (summaryOpt.isEmpty()) continue;

            ReelSummary summary = summaryOpt.get();
            List<String> keyPoints = parseJsonList(summary.getKeyPoints());
            List<String> tools = parseJsonList(summary.getToolsOrProducts());
            List<String> topics = parseJsonList(summary.getTopics());

            // Determine requester
            String senderId = null;
            String senderUsername = null;
            String senderName = null;
            String senderAvatar = null;
            List<ReelRequest> reqs = requestRepository.findByReelId(reel.getId());
            if (!reqs.isEmpty()) {
                senderId = reqs.get(0).getInstagramSenderId();
                if (senderId != null) {
                    Optional<InstagramUserProfile> profile = userProfileService.getProfile(senderId);
                    if (profile.isPresent()) {
                        senderUsername = profile.get().getUsername();
                        senderName = profile.get().getName();
                        senderAvatar = profile.get().getProfilePic();
                    }
                }
            }

            String sourceTitle = summary.getTitle() != null && !summary.getTitle().isBlank()
                    ? summary.getTitle() : "Reel Insight";

            if (!tools.isEmpty()) {
                for (String tool : tools) {
                    ExtractedKnowledgeItem item = ExtractedKnowledgeItem.builder()
                            .reel(reel)
                            .title(tool)
                            .category("Tools")
                            .subCategory("AI & Automation")
                            .summary(summary.getSummary())
                            .keyData(!keyPoints.isEmpty() ? keyPoints.get(0) : null)
                            .requesterId(senderId)
                            .requesterUsername(senderUsername)
                            .requesterName(senderName)
                            .requesterAvatarUrl(senderAvatar)
                            .sourceReelUrl(reel.getSourceUrl())
                            .sourceReelTitle(sourceTitle)
                            .build();
                    itemRepository.save(item);
                    created++;
                }
            } else {
                ExtractedKnowledgeItem item = ExtractedKnowledgeItem.builder()
                        .reel(reel)
                        .title(sourceTitle)
                        .category("Insights & Resources")
                        .subCategory(!topics.isEmpty() ? topics.get(0) : "General")
                        .summary(summary.getSummary())
                        .keyData(!keyPoints.isEmpty() ? String.join("; ", keyPoints) : null)
                        .requesterId(senderId)
                        .requesterUsername(senderUsername)
                        .requesterName(senderName)
                        .requesterAvatarUrl(senderAvatar)
                        .sourceReelUrl(reel.getSourceUrl())
                        .sourceReelTitle(sourceTitle)
                        .build();
                itemRepository.save(item);
                created++;
            }
        }
        return created;
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private ExtractedKnowledgeItemDto toDto(ExtractedKnowledgeItem item) {
        List<String> tags = Collections.emptyList();
        if (item.getTags() != null && !item.getTags().isBlank()) {
            try {
                tags = objectMapper.readValue(item.getTags(), new TypeReference<List<String>>() {});
            } catch (Exception ignored) {}
        }

        return ExtractedKnowledgeItemDto.builder()
                .id(item.getId())
                .reelId(item.getReel().getId())
                .title(item.getTitle())
                .category(item.getCategory())
                .subCategory(item.getSubCategory())
                .summary(item.getSummary())
                .keyData(item.getKeyData())
                .url(item.getUrl())
                .tags(tags)
                .requesterId(item.getRequesterId())
                .requesterUsername(item.getRequesterUsername())
                .requesterName(item.getRequesterName())
                .requesterAvatarUrl(item.getRequesterAvatarUrl())
                .sourceReelUrl(item.getSourceReelUrl())
                .sourceReelTitle(item.getSourceReelTitle())
                .createdAt(item.getCreatedAt())
                .build();
    }
}
