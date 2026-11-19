package com.example.instagramreelai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    private Jwt jwt = new Jwt();
    private OpenAi openai = new OpenAi();
    private Instagram instagram = new Instagram();
    private Media media = new Media();
    private Cors cors = new Cors();
    private Admin admin = new Admin();

    @Getter @Setter
    public static class Jwt {
        private String secret;
        private long expirationMs = 86400000;
    }

    @Getter @Setter
    public static class OpenAi {
        private String apiKey;
        private String transcriptionModel = "whisper-1";
        private String summaryModel = "gpt-4o";
    }

    @Getter @Setter
    public static class Instagram {
        private String appId;
        private String appSecret;
        private String accessToken;
        private String accountId;
        private String webhookVerifyToken;
        private String oauthRedirectUri = "http://localhost:8085/api/instagram/oauth/callback";
        private String frontendRedirectUri = "http://localhost:3002/instagram";
        private String oauthAuthorizeUrl = "https://www.instagram.com/oauth/authorize";
        private String oauthTokenUrl = "https://api.instagram.com/oauth/access_token";
        private String graphApiBase = "https://graph.instagram.com";
        private String graphApiVersion = "v26.0";
        private String oauthScopes = "instagram_business_basic,instagram_business_manage_messages";
        private long oauthStateTtlMinutes = 10;
        private boolean webhookSignatureRequired = true;
    }

    @Getter @Setter
    public static class Media {
        private String tempDir = "/tmp/instagram-reel-ai";
        private int maxSizeMb = 100;
        private String ffmpegPath = "ffmpeg";
    }

    @Getter @Setter
    public static class Cors {
        private String allowedOrigins = "http://localhost:5173";
    }

    @Getter @Setter
    public static class Admin {
        private String username = "admin";
        private String password = "admin123";
    }
}
