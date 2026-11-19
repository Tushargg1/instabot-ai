package com.example.instagramreelai.service;

import com.example.instagramreelai.entity.AppUser;
import com.example.instagramreelai.entity.InstagramOAuthState;
import com.example.instagramreelai.repository.InstagramOAuthStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class InstagramOAuthStateService {

    private final InstagramOAuthStateRepository stateRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String create(AppUser user, long ttlMinutes) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        stateRepository.save(InstagramOAuthState.builder()
                .stateHash(hash(state))
                .user(user)
                .expiresAt(Instant.now().plusSeconds(ttlMinutes * 60))
                .build());
        return state;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AppUser consume(String state) {
        InstagramOAuthState stored = stateRepository.findByStateHashAndConsumedAtIsNull(hash(state))
                .orElseThrow(() -> new IllegalArgumentException("Invalid or already used OAuth state"));
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            stored.setConsumedAt(Instant.now());
            stateRepository.save(stored);
            throw new IllegalArgumentException("OAuth state has expired. Please connect again.");
        }
        stored.setConsumedAt(Instant.now());
        stateRepository.save(stored);
        return stored.getUser();
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to secure OAuth state", e);
        }
    }
}
