package com.example.instagramreelai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketingLeadRequest {
    private String instagramUsername;
    private String instagramSenderId;
    private String contactName;
    private String avatarUrl;
    private String context;
    private String tag;
    private String productOffer;
    private String salesGoal;
    private String firstMessage;
    private String status;
    private String notes;
}
