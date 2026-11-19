package com.example.instagramreelai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class PublicSubmissionTokenDtos {
    private PublicSubmissionTokenDtos() {
    }

    public record CreateRequest(
            @Min(60) @Max(86400) Long expiresInSeconds,
            @Size(max = 120) String publicLabel) {
    }

    public record CreateResponse(String token, Instant expiresAt) {
    }

    public record MetadataResponse(String status, String publicLabel, Instant expiresAt) {
    }

    public record ConsumeResponse(
            UUID validationId,
            String status,
            String integrationHook,
            Instant consumedAt) {
    }
}