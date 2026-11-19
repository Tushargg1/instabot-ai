package com.example.instagramreelai.ai;

import com.example.instagramreelai.dto.AiSummaryResult;

/**
 * Summarizes a transcript using AI.
 */
public interface SummaryService {
    AiSummaryResult summarize(String transcript);
}
