package com.example.instagramreelai.service;

import com.example.instagramreelai.dto.ReelDto;
import com.example.instagramreelai.dto.ReelListDto;
import com.example.instagramreelai.entity.Reel;
import com.example.instagramreelai.entity.ReelSummary;
import com.example.instagramreelai.entity.ReelTranscript;
import com.example.instagramreelai.repository.ReelRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReelService {

    private final ReelRepository reelRepository;
    private final ObjectMapper objectMapper;

    public Page<ReelListDto> listReels(UUID userId, Pageable pageable, String status) {
        Page<Reel> reels;
        if (status != null && !status.isBlank()) {
            reels = reelRepository.findByUserIdAndProcessingStatus(userId, status, pageable);
        } else {
            reels = reelRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return reels.map(this::toListDto);
    }

    @Transactional(readOnly = true)
    public Optional<ReelDto> getReelById(UUID id, UUID userId) {
        return reelRepository.findById(id)
                .filter(r -> r.getUser() != null && r.getUser().getId().equals(userId))
                .map(this::toDetailDto);
    }

    @Transactional
    public void deleteReel(UUID id, UUID userId) {
        reelRepository.findById(id)
                .filter(r -> r.getUser() != null && r.getUser().getId().equals(userId))
                .ifPresent(reelRepository::delete);
    }

    public Page<ReelListDto> searchReels(UUID userId, String query, Pageable pageable) {
        return reelRepository.searchReelsByUser(userId, query, pageable).map(this::toListDto);
    }

    private ReelListDto toListDto(Reel reel) {
        ReelSummary summary = reel.getSummary();
        String title = summary != null ? summary.getTitle() : null;
        String excerpt = summary != null && summary.getSummary() != null ?
                (summary.getSummary().length() > 150 ?
                        summary.getSummary().substring(0, 150) + "..." : summary.getSummary()) : null;
        List<String> topics = summary != null ? parseJsonList(summary.getTopics()) : Collections.emptyList();

        return ReelListDto.builder()
                .id(reel.getId())
                .title(title)
                .summaryExcerpt(excerpt)
                .creatorUsername(reel.getCreatorUsername())
                .processingStatus(reel.getProcessingStatus())
                .topics(topics)
                .savedAt(reel.getSavedAt())
                .createdAt(reel.getCreatedAt())
                .build();
    }

    private ReelDto toDetailDto(Reel reel) {
        ReelSummary summary = reel.getSummary();
        ReelTranscript transcript = reel.getTranscript();

        ReelDto.ReelDtoBuilder builder = ReelDto.builder()
                .id(reel.getId())
                .sourceUrl(reel.getSourceUrl())
                .externalMediaId(reel.getExternalMediaId())
                .creatorUsername(reel.getCreatorUsername())
                .caption(reel.getCaption())
                .processingStatus(reel.getProcessingStatus())
                .savedAt(reel.getSavedAt())
                .createdAt(reel.getCreatedAt());

        if (summary != null) {
            builder.title(summary.getTitle())
                    .summaryText(summary.getSummary())
                    .keyPoints(parseJsonList(summary.getKeyPoints()))
                    .actionItems(parseJsonList(summary.getActionItems()))
                    .topics(parseJsonList(summary.getTopics()))
                    .toolsOrProducts(parseJsonList(summary.getToolsOrProducts()))
                    .peopleOrCompanies(parseJsonList(summary.getPeopleOrCompanies()))
                    .claimsToVerify(parseJsonList(summary.getClaimsToVerify()))
                    .language(summary.getLanguage())
                    .confidence(summary.getConfidence() != null ? summary.getConfidence().doubleValue() : null);
        }

        if (transcript != null) {
            builder.transcript(transcript.getTranscript())
                    .durationSeconds(transcript.getDurationSeconds() != null ?
                            transcript.getDurationSeconds().doubleValue() : null);
        }

        return builder.build();
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
