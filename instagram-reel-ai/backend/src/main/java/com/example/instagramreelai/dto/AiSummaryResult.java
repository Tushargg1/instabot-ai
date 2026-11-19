package com.example.instagramreelai.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiSummaryResult {
    private String title;
    private String summary;
    private List<String> keyPoints;
    private List<String> actionItems;
    private List<String> topics;
    private List<String> toolsOrProducts;
    private List<String> peopleOrCompanies;
    private List<String> claimsToVerify;
    private String language;
    private Double confidence;
}
