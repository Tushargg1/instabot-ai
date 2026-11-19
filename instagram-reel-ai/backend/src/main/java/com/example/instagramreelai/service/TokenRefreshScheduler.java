package com.example.instagramreelai.service;

import com.example.instagramreelai.config.AppProperties;
import com.example.instagramreelai.entity.InstagramAccount;
import com.example.instagramreelai.repository.InstagramAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Automatically refreshes Instagram long-lived User Access Tokens (valid for 60 days).
 * Meta allows refreshing active tokens via GET /refresh_access_token.
 * By refreshing every 30 days, tokens never expire and run 24/7 forever without manual intervention.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRefreshScheduler {

    private static final String REFRESH_URL = "https://graph.instagram.com/refresh_access_token";

    private final InstagramAccountRepository accountRepository;
    private final EncryptionService encryptionService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * Check and refresh on startup (non-blocking).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        Thread t = new Thread(this::checkAndRefreshTokens);
        t.setName("token-refresh-startup");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Scheduled daily at 03:00 AM.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void dailyTokenRefresh() {
        checkAndRefreshTokens();
    }

    @Transactional
    public void checkAndRefreshTokens() {
        log.info("Running automated 60-day Instagram access token refresh check...");
        List<InstagramAccount> accounts = accountRepository.findAll().stream()
                .filter(a -> Boolean.TRUE.equals(a.getActive()))
                .toList();

        for (InstagramAccount account : accounts) {
            try {
                String currentToken = appProperties.getInstagram().getAccessToken();
                if ((currentToken == null || currentToken.isBlank()) && account.getAccessTokenEncrypted() != null) {
                    currentToken = encryptionService.decrypt(account.getAccessTokenEncrypted());
                }

                if (currentToken == null || currentToken.isBlank() || currentToken.startsWith("mock_")) {
                    continue;
                }

                // Check if account was updated more than 20 days ago (or on startup check)
                Instant lastUpdated = account.getUpdatedAt() != null ? account.getUpdatedAt() : account.getCreatedAt();
                long daysSinceUpdate = Duration.between(lastUpdated, Instant.now()).toDays();

                // Only refresh if token is at least 7 days old to avoid spamming Meta's endpoint
                if (daysSinceUpdate < 7) {
                    log.info("Token for account {} is only {} days old; refresh not needed yet (target is 30 days)",
                            account.getInstagramAccountId(), daysSinceUpdate);
                    continue;
                }

                String url = REFRESH_URL + "?grant_type=ig_refresh_token&access_token=" + currentToken;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .timeout(Duration.ofSeconds(15))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode json = objectMapper.readTree(response.body());
                    String newToken = json.path("access_token").asText(null);
                    long expiresIn = json.path("expires_in").asLong(0);

                    if (newToken != null && !newToken.isBlank()) {
                        account.setAccessTokenEncrypted(encryptionService.encrypt(newToken));
                        account.setUpdatedAt(Instant.now());
                        accountRepository.save(account);

                        appProperties.getInstagram().setAccessToken(newToken);
                        log.info("✅ SUCCESS: Refreshed Instagram access token for account {}! Expires in {} days ({} seconds)",
                                account.getInstagramAccountId(), expiresIn / 86400, expiresIn);
                    }
                } else {
                    log.warn("Instagram token refresh returned status {}: {}", response.statusCode(), response.body());
                }
            } catch (Exception e) {
                log.warn("Error refreshing token for account {}: {}", account.getInstagramAccountId(), e.getMessage());
            }
        }
    }
}
