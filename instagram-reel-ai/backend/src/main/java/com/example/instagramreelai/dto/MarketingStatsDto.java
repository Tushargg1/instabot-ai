package com.example.instagramreelai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketingStatsDto {
    private long totalLeads;
    private long pending;
    private long outreached;
    private long inConversation;
    private long converted;
    private long closed;
    private double conversionRate;
}
