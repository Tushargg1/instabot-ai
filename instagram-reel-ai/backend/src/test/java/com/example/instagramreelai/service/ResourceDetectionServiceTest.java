package com.example.instagramreelai.service;

import com.example.instagramreelai.dto.ResourceDetectionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceDetectionServiceTest {

    private ResourceDetectionService service;

    @BeforeEach
    void setUp() {
        service = new ResourceDetectionService(null, null, null);
    }

    @Test
    void urlHasHighestPriorityAndIsNormalized() {
        ResourceDetectionResult result = service.detect(
                "Comment GUIDE, then visit www.example.com/resource.", null);

        assertThat(result.status()).isEqualTo("AVAILABLE");
        assertThat(result.resourceType()).isEqualTo("URL");
        assertThat(result.resourceUrl()).isEqualTo("https://www.example.com/resource");
        assertThat(result.requiredAction()).isNull();
    }

    @Test
    void extractsCommentKeyword() {
        ResourceDetectionResult result = service.detect("Comment the word guide", null);

        assertThat(result.status()).isEqualTo("ACTION_REQUIRED");
        assertThat(result.resourceType()).isEqualTo("COMMENT_KEYWORD");
        assertThat(result.requiredAction()).isEqualTo("COMMENT");
        assertThat(result.actionPayload()).isEqualTo("GUIDE");
    }

    @Test
    void skipsNonKeywordAndUsesLaterCommentKeyword() {
        ResourceDetectionResult result = service.detect("Comment below, then comment TOOLKIT", null);

        assertThat(result.actionPayload()).isEqualTo("TOOLKIT");
    }

    @Test
    void extractsDmKeywordFromCaptionFallback() {
        ResourceDetectionResult result = service.detect(null, "DM me recipe");

        assertThat(result.resourceType()).isEqualTo("DM_KEYWORD");
        assertThat(result.requiredAction()).isEqualTo("DM");
        assertThat(result.actionPayload()).isEqualTo("RECIPE");
        assertThat(result.evidenceSource()).isEqualTo("caption");
    }

    @Test
    void detectsLinkInBioBeforeFollow() {
        ResourceDetectionResult result = service.detect(
                "Follow me and check the link in my bio", null);

        assertThat(result.resourceType()).isEqualTo("LINK_IN_BIO");
        assertThat(result.requiredAction()).isEqualTo("LINK_IN_BIO");
    }

    @Test
    void detectsFollowRequirement() {
        ResourceDetectionResult result = service.detect("Follow @creator for the template", null);

        assertThat(result.resourceType()).isEqualTo("FOLLOW_REQUIRED");
        assertThat(result.requiredAction()).isEqualTo("FOLLOW");
    }

    @Test
    void reportsNotFoundForUnrelatedText() {
        ResourceDetectionResult result = service.detect("A short educational reel", "No resource offered");

        assertThat(result.status()).isEqualTo("NOT_FOUND");
        assertThat(result.resourceType()).isNull();
        assertThat(result.requiresManualAction()).isFalse();
    }
}
