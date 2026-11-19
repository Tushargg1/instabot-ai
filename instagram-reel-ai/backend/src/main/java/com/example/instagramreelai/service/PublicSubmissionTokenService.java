package com.example.instagramreelai.service;

import com.example.instagramreelai.dto.PublicSubmissionTokenDtos.CreateResponse;
import com.example.instagramreelai.dto.PublicSubmissionTokenDtos.MetadataResponse;
import com.example.instagramreelai.entity.AppUser;
import com.example.instagramreelai.entity.PublicSubmissionToken;
import com.example.instagramreelai.repository.PublicSubmissionTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class PublicSubmissionTokenService {
    public static final String INTEGRATION_HOOK = "PUBLIC_REEL_SUBMISSION_VALIDATED";
    private static final int TOKEN_BYTES = 32;
    private static final long MAX_TTL_SECONDS = 86_400;

    private final PublicSubmissionTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long defaultTtlSeconds;

    public PublicSubmissionTokenService(
            PublicSubmissionTokenRepository repository,
            @Value("${app.public-submission-token.default-ttl-seconds:900}") long defaultTtlSeconds) {
        this.repository = repository;
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    @Transactional
    public CreateResponse create(AppUser owner, Long requestedTtlSeconds, String publicLabel) {
        long ttl = requestedTtlSeconds == null ? defaultTtlSeconds : requestedTtlSeconds;
        if (ttl < 60 || ttl > MAX_TTL_SECONDS) {
            throw new IllegalArgumentException("Token lifetime must be between 60 and 86400 seconds");
        }
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant expiresAt = Instant.now().plusSeconds(ttl);
        repository.save(PublicSubmissionToken.builder()
                .user(owner)
                .tokenDigest(digest(tokenBytes))
                .publicLabel(normalizeLabel(publicLabel))
                .expiresAt(expiresAt)
                .build());
        return new CreateResponse(rawToken, expiresAt);
    }

    @Transactional(readOnly = true)
    public MetadataResponse metadata(String rawToken) {
        PublicSubmissionToken stored = find(rawToken);
        Instant now = Instant.now();
        String status = stored.getConsumedAt() != null ? "CONSUMED"
                : !stored.getExpiresAt().isAfter(now) ? "EXPIRED" : "VALID";
        return new MetadataResponse(status, stored.getPublicLabel(), stored.getExpiresAt());
    }
    /**
     * Atomic validation/consumption boundary for the future Reel ingestion integration.
     * The owner id is intentionally available only to trusted backend code.
     */
    @Transactional
    public ValidatedSubmission consumeForIntegration(String rawToken) {
        PublicSubmissionToken stored = find(rawToken);
        Instant now = Instant.now();
        if (stored.getConsumedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Submission token was already used");
        }
        if (!stored.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Submission token has expired");
        }
        if (repository.consumeIfValid(stored.getId(), now) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Submission token is no longer available");
        }
        return new ValidatedSubmission(stored.getId(), stored.getUser().getId(), now,
                INTEGRATION_HOOK);
    }

    private PublicSubmissionToken find(String rawToken) {
        return repository.findByTokenDigest(digest(decodeCanonicalToken(rawToken)))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Submission token not found"));
    }

    private byte[] decodeCanonicalToken(String rawToken) {
        if (rawToken == null || rawToken.length() > 128) {
            throw tokenNotFound();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(rawToken);
            String canonical = Base64.getUrlEncoder().withoutPadding().encodeToString(decoded);
            if (decoded.length != TOKEN_BYTES || !canonical.equals(rawToken)) {
                throw tokenNotFound();
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw tokenNotFound();
        }
    }

    private ResponseStatusException tokenNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission token not found");
    }

    private String digest(byte[] tokenBytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(tokenBytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalizeLabel(String label) {
        return label == null || label.isBlank() ? null : label.trim();
    }

    public record ValidatedSubmission(
            UUID validationId,
            UUID ownerUserId,
            Instant consumedAt,
            String integrationHook) {
    }
}