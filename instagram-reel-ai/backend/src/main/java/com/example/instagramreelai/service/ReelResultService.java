package com.example.instagramreelai.service;

import com.example.instagramreelai.ai.TranscriptionService;
import com.example.instagramreelai.dto.AiSummaryResult;
import com.example.instagramreelai.entity.Reel;
import com.example.instagramreelai.entity.ReelSummary;
import com.example.instagramreelai.entity.ReelTranscript;
import com.example.instagramreelai.repository.ReelSummaryRepository;
import com.example.instagramreelai.repository.ReelTranscriptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ReelResultService {
    private final ReelTranscriptRepository transcriptRepository;
    private final ReelSummaryRepository summaryRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void save(Reel reel, TranscriptionService.TranscriptionResult transcription,
                     AiSummaryResult result) {
        ReelTranscript transcript = transcriptRepository.findByReelId(reel.getId())
                .orElseGet(() -> ReelTranscript.builder().reel(reel).build());
        transcript.setTranscript(transcription.text());
        transcript.setLanguage(transcription.language());
        transcript.setDurationSeconds(transcription.durationSeconds() == null ? null
                : BigDecimal.valueOf(transcription.durationSeconds()));
        transcriptRepository.save(transcript);

        ReelSummary summary = summaryRepository.findByReelId(reel.getId())
                .orElseGet(() -> ReelSummary.builder().reel(reel).build());
        summary.setTitle(result.getTitle());
        summary.setSummary(result.getSummary());

        try {
            summary.setKeyPoints(objectMapper.writeValueAsString(result.getKeyPoints()));
            summary.setActionItems(objectMapper.writeValueAsString(result.getActionItems()));
            summary.setTopics(objectMapper.writeValueAsString(result.getTopics()));
            summary.setToolsOrProducts(objectMapper.writeValueAsString(result.getToolsOrProducts()));
            summary.setPeopleOrCompanies(objectMapper.writeValueAsString(result.getPeopleOrCompanies()));
            summary.setClaimsToVerify(objectMapper.writeValueAsString(result.getClaimsToVerify()));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize structured AI result", exception);
        }
        summary.setLanguage(result.getLanguage());
        summary.setConfidence(result.getConfidence() == null ? null
                : BigDecimal.valueOf(result.getConfidence()));
        summaryRepository.save(summary);
    }
}
