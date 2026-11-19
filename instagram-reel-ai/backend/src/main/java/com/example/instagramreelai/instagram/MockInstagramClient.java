package com.example.instagramreelai.instagram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Mock implementation for local development and testing.
 * Simulates Instagram API responses without requiring real credentials.
 */
@Slf4j
@Component
@Primary
@Profile("dev")
public class MockInstagramClient implements InstagramClient {

    @Override
    public Optional<String> resolveMediaUrl(String mediaId, String accessToken) {
        log.info("[MOCK] Resolving media URL for mediaId={}", mediaId);
        // Simulate a successful media URL resolution
        return Optional.of("https://mock-cdn.example.com/media/" + mediaId + ".mp4");
    }

    @Override
    public boolean sendReply(String accountId, String recipientId, String message, String accessToken) {
        log.info("[MOCK] Sending reply to recipientId={}: {}", recipientId, message);
        return true;
    }

    @Override
    public boolean verifyWebhook(String verifyToken, String challenge) {
        log.info("[MOCK] Verifying webhook with token={}", verifyToken);
        return true;
    }
}
