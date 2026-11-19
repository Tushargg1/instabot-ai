package com.example.instagramreelai.service;

import com.example.instagramreelai.config.AppProperties;
import com.example.instagramreelai.entity.InstagramAccount;
import com.example.instagramreelai.instagram.InstagramClient;
import com.example.instagramreelai.instagram.InstagramUserProfile;
import com.example.instagramreelai.repository.InstagramAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstagramUserProfileService {

    private final InstagramClient instagramClient;
    private final InstagramAccountRepository accountRepository;
    private final EncryptionService encryptionService;
    private final AppProperties appProperties;

    private static class CacheEntry {
        final InstagramUserProfile profile;
        final Instant expiresAt;

        CacheEntry(InstagramUserProfile profile, long ttlSeconds) {
            this.profile = profile;
            this.expiresAt = Instant.now().plusSeconds(ttlSeconds);
        }

        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public Optional<InstagramUserProfile> getProfile(String igsid) {
        if (igsid == null || igsid.isBlank()) {
            return Optional.empty();
        }

        CacheEntry entry = cache.get(igsid);
        if (entry != null && entry.isValid()) {
            return Optional.of(entry.profile);
        }

        String accessToken = resolveAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("Cannot fetch profile for igsid={}: no access token available", igsid);
            return Optional.empty();
        }

        Optional<InstagramUserProfile> profileOpt = instagramClient.getUserProfile(igsid, accessToken);
        profileOpt.ifPresent(p -> cache.put(igsid, new CacheEntry(p, 1800))); // 30 min cache
        return profileOpt;
    }

    private String resolveAccessToken() {
        String configuredToken = appProperties.getInstagram().getAccessToken();
        if (configuredToken != null && !configuredToken.isBlank()) {
            return configuredToken;
        }

        String accountId = appProperties.getInstagram().getAccountId();
        if (accountId != null && !accountId.isBlank()) {
            Optional<InstagramAccount> accountOpt = accountRepository.findByInstagramAccountId(accountId);
            if (accountOpt.isPresent() && accountOpt.get().getAccessTokenEncrypted() != null) {
                try {
                    return encryptionService.decrypt(accountOpt.get().getAccessTokenEncrypted());
                } catch (Exception e) {
                    log.error("Failed to decrypt account token", e);
                }
            }
        }

        return null;
    }
}
