package com.example.instagramreelai.dto;

import lombok.Data;

@Data
public class UserSettingsDto {
    private String openaiApiKey;
    private String openaiTranscriptionModel;
    private String openaiSummaryModel;
    private String instagramAppId;
    private String instagramAppSecret;
    private String instagramAccessToken;
    private String instagramAccountId;
    private String instagramWebhookVerifyToken;
}
