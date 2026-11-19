package com.example.instagramreelai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketingChatMessage {
    private String sender; // "USER", "PROSPECT", "BOT"
    private String text;
    private Instant timestamp;
}
