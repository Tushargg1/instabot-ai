package com.example.instagramreelai.dto;

public record ResourceDetectionResult(
        String status,
        String resourceType,
        String resourceUrl,
        String requiredAction,
        String actionPayload,
        String evidenceSource
) {
    public boolean requiresManualAction() {
        return requiredAction != null;
    }
}
